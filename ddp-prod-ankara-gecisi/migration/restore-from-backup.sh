#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# R6 — SOĞUK YEDEKTEN GERİ YÜKLEME  (son çare)
#
# ÖNCE BUNU OKU:
#
# Bu, ES snapshot restore'u DEĞİL. Soğuk kopyayı tek bir node'a geri koyup
# "bitti" diyemezsin — çünkü cluster'daki diğer kopya daha yeni olur, ES senin
# geri koyduğun eski kopyayı BAYAT sayar ve üzerine yenisini kopyalar. Hiçbir
# şey geri gelmez.
#
# Bu yüzden geri yükleme TÜM CLUSTER'I kapsar ve `unsafe-bootstrap` gerektirir.
# Yıkıcıdır, veri kaybı riski taşır ve ancak şu durumda kullanılır:
#   cluster verisi/metadata'sı bozuldu ve R1-R5 işe yaramadı.
#
# ÖNCE ŞUNU SOR: veri kaybı yaşamadıysan muhtemelen ./rollback.sh yeterlidir.
# Migration'ı geri almak için bu script GEREKMİYOR.
#
#   ./restore-from-backup.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_tools
[[ -f "${ARTIFACT_DIR}/LAST_BACKUP" ]] || die "Yedek kaydı yok: ${ARTIFACT_DIR}/LAST_BACKUP"
# shellcheck source=/dev/null
source "${ARTIFACT_DIR}/LAST_BACKUP"

section "R6 — SOĞUK YEDEKTEN GERİ YÜKLEME"
cat <<EOF
  Yedek tarihi    : ${timestamp}
  Doküman sayısı  : ${doc_count}   ·   Shard: ${shards}
  Shard verisi    : ${data_node_ip}:${data_tarball}
  Cluster state   : ${master_node_ip}:${master_tarball}
  Metadata dump   : ${dump_dir}
EOF

# --- Gerçekten gerekli mi? -------------------------------------------------
section "Önce: gerçekten buna mı ihtiyacın var?"
cat <<'EOF'
  Aşağıdakilerden BİRİ geçerliyse bu script'e ihtiyacın YOK:

    * Ankara node'ları sorun çıkardı, geri dönmek istiyorsun
        -> ./rollback.sh drain-ankara
    * Awareness sonrası cluster yellow'da takıldı
        -> ./rollback.sh awareness
    * WAN doydu, prod yavaşladı
        -> ./rollback.sh throttle
    * .28'i geri almak istiyorsun
        -> ./rollback.sh readd-master3

  Bu script SADECE şunun içindir: index verisi veya cluster metadata'sı bozuldu,
  yukarıdakilerin hiçbiri düzeltmedi.
EOF
confirm "Yukarıdakileri denedim, hiçbiri işe yaramadı"

# --- Mevcut durumu kaydet --------------------------------------------------
if es GET /_cluster/health >/dev/null 2>&1; then
  section "Mevcut (bozuk) durum kaydediliyor"
  BROKEN="${ARTIFACT_DIR}/broken-state-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "${BROKEN}"
  es GET '/_cluster/health?level=indices' | jq '.' > "${BROKEN}/health.json"       2>/dev/null || true
  es GET '/_cat/shards?v'                          > "${BROKEN}/shards.txt"        2>/dev/null || true
  es GET '/_cat/indices?v'                         > "${BROKEN}/indices.txt"       2>/dev/null || true
  es GET '/_cluster/settings?flat_settings=true' | jq '.' > "${BROKEN}/settings.json" 2>/dev/null || true
  ok "Kaydedildi: ${BROKEN}  (sonradan ne olduğunu anlamak için)"
  CUR_DOCS="$(es GET '/_all/_count?expand_wildcards=all' 2>/dev/null | jq -r '.count // "?"')"
  warn "Cluster şu an ${CUR_DOCS} doküman görüyor. Yedekte ${doc_count} var."
  warn "Yedeğe dönersen ${timestamp} tarihinden SONRAKİ tüm yazmalar KAYBOLUR."
else
  warn "Cluster'a ulaşılamıyor — muhtemelen zaten çökmüş durumda."
fi

# --- Onay ------------------------------------------------------------------
section "!!! YIKICI İŞLEM !!!"
cat <<EOF
  Yapılacaklar:
    1. TÜM ES node'ları durdurulacak (İstanbul + varsa Ankara)
    2. ${master_node} : cluster state yedekten geri konacak
    3. Diğer master'lar (${IST_MASTER_2_NAME}, ${IST_MASTER_3_NAME}) : veri klasörleri SİLİNECEK
    4. ${master_node} üzerinde unsafe-bootstrap çalıştırılacak
    5. ${data_node} : shard verisi yedekten geri konacak
    6. ${IST_DATA_4_NAME} : veri klasörü SİLİNECEK (${data_node}'dan yeniden kopyalanacak)
    7. Cluster sırayla ayağa kaldırılacak

  Ankara node'ları varsa onlar da sıfırlanacak (zaten kopya veri tutuyorlar).
EOF
confirm "Okudum, anladım, devam"
read -rp "Onaylamak için cluster adını yazın (${CLUSTER_NAME}): " TYPED
[[ "${TYPED}" == "${CLUSTER_NAME}" ]] || die "Yanlış cluster adı. İptal."

# --- Adımlar ---------------------------------------------------------------
section "ADIM 1 — Yazma trafiğini durdur"
echo "  index-api, cms-catalog ve ES'e yazan tüm servisleri durdurun."
confirm "Yazma trafiği durduruldu mu?"

section "ADIM 2 — Tüm ES node'larını durdur"
cat <<EOF
  Sırasız, hepsinde:

      systemctl stop elasticsearch

  İstanbul : ${IST_MASTER_1_IP} ${IST_MASTER_2_IP} ${IST_MASTER_3_IP} ${IST_DATA_4_IP} ${IST_DATA_5_IP}
  Ankara   : ${ANK_MASTER_1_IP} ${ANK_DATA_1_IP} ${ANK_DATA_2_IP}   (kurulduysa)
EOF
confirm "Tüm node'lar durduruldu mu?"

section "ADIM 3 — Cluster state'i geri koy (${master_node})"
cat <<EOF
  ${master_node_ip} üzerinde:

      mv /data/elasticsearch-master /data/elasticsearch-master.bozuk-\$(date +%s)
      mkdir -p /data
      tar xzf ${master_tarball} -C /data
      chown -R elasticsearch:elasticsearch /data/elasticsearch-master

  Eski klasörü SİLMEYİN, yeniden adlandırın — geri dönüşün geri dönüşü lazım olabilir.
EOF
confirm "Cluster state geri kondu mu?"

section "ADIM 4 — Diğer master'ların klasörlerini temizle"
cat <<EOF
  ${IST_MASTER_2_IP} ve ${IST_MASTER_3_IP} üzerinde:

      mv /data/elasticsearch-master /data/elasticsearch-master.eski-\$(date +%s)

  NEDEN: bu node'ların cluster state'i yedektekinden YENİ. Bırakırsanız hangi
  state'in doğru olduğu çelişir ve cluster kalkmaz. Boş başlayıp
  ${master_node}'dan öğrenecekler. Master node'da shard verisi yok, kayıp yok.
EOF
confirm "Diğer master klasörleri temizlendi mi?"

section "ADIM 5 — unsafe-bootstrap (${master_node})"
cat <<EOF
  ${master_node_ip} üzerinde:

      /app/elasticsearch-*/bin/elasticsearch-node unsafe-bootstrap

  Çıkan uyarıyı okuyup onaylayın. Bu komut, ${master_node}'un cluster state'ini
  tek doğru kaynak kabul edip yeni bir cluster oluşturur.

  Adı boşuna "unsafe" değil — ama tüm node'lar kapalı ve elimizdeki tek tutarlı
  state bu olduğu için burada doğru araç.
EOF
confirm "unsafe-bootstrap çalıştı mı?"

section "ADIM 6 — Shard verisini geri koy (${data_node})"
cat <<EOF
  ${data_node_ip} üzerinde:

      mv ${data_path} ${data_path}.bozuk-\$(date +%s)
      tar xzf ${data_tarball} -C "$(dirname "${data_path}")"
      chown -R elasticsearch:elasticsearch ${data_path}

  ${IST_DATA_4_IP} üzerinde (diğer data node — verisi bayat, temizlenecek):

      mv /data/elasticsearch-data /data/elasticsearch-data.eski-\$(date +%s)

  ${IST_DATA_4_NAME} boş başlayıp replica'ları ${data_node}'dan kopyalayacak.
EOF
confirm "Shard verisi geri kondu mu?"

section "ADIM 7 — Cluster'ı sırayla ayağa kaldır"
cat <<EOF
  SIRA ÖNEMLİ:

    1. ${master_node} (${master_node_ip})          -> bekleyin, ayağa kalksın
    2. ${IST_MASTER_2_NAME} (${IST_MASTER_2_IP})
    3. ${IST_MASTER_3_NAME} (${IST_MASTER_3_IP})
    4. ${data_node} (${data_node_ip})
    5. ${IST_DATA_4_NAME} (${IST_DATA_4_IP})

  Ankara node'larını ŞİMDİLİK BAŞLATMAYIN — önce İstanbul'un sağlam ayağa
  kalktığını görün.
EOF
confirm "İstanbul node'ları sırayla başlatıldı mı?"

# --- Doğrulama -------------------------------------------------------------
section "Doğrulama"
log "Cluster'ın cevap vermesi bekleniyor…"
for i in $(seq 1 60); do
  if es GET /_cluster/health >/dev/null 2>&1; then ok "Cluster cevap veriyor."; break; fi
  printf '%s  … (%s/60)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 10
  if (( i == 60 )); then die "Cluster 10 dakikada ayağa kalkmadı. Logları inceleyin:
     tail -200 /app/elasticsearch-*/logs/${CLUSTER_NAME}.log"; fi
done

es GET '/_cat/nodes?v&h=name,ip,node.role,master&s=name'
echo
wait_for_green 3600 || warn "Green'e ulaşılamadı — ${IST_DATA_4_NAME} replica'ları hâlâ kopyalıyor olabilir."

RESTORED_DOCS="$(es GET '/_all/_count?expand_wildcards=all' | jq -r '.count // 0')"
log "Geri yüklenen doküman : ${RESTORED_DOCS}"
log "Yedekteki doküman     : ${doc_count}"
[[ "${RESTORED_DOCS}" == "${doc_count}" ]] \
  && ok "Doküman sayısı birebir tutuyor." \
  || warn "Sayılar farklı — index'leri tek tek karşılaştırın: ${dump_dir}/indices.txt"

echo
es GET '/_cat/indices?v&h=index,health,status,pri,rep,docs.count,store.size&s=index'

section "SONRAKİ ADIMLAR"
cat <<EOF
  [ ] ${dump_dir}/indices.txt ile karşılaştırın — index sayısı ve doc sayıları tutuyor mu
  [ ] Template / ILM / alias kontrol edin (${dump_dir} altındaki JSON'larla)
  [ ] index-api / search-api üzerinden fonksiyonel test
  [ ] Yazma trafiğini geri açın
  [ ] .bozuk-* ve .eski-* klasörlerini birkaç gün SİLMEYİN
  [ ] Ne olduğunu yazın — aynı hataya girmeden migration'ı tekrar planlamayın
EOF
