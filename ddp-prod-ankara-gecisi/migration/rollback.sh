#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# ROLLBACK — senaryo bazlı geri alma
#
# Senaryoların tam açıklaması ve karar ağacı: ROLLBACK.md
#
#   ./rollback.sh status          Mevcut migration ayarlarını göster (salt okunur)
#   ./rollback.sh awareness       R2: Awareness'ı kapat, shard'lar İstanbul'a dönsün
#   ./rollback.sh throttle        R3: Recovery throttle'ı sıfırla / durdur
#   ./rollback.sh drain-ankara    R1: Ankara node'larını boşalt (kapatmaya hazırla)
#   ./rollback.sh readd-master3   R4: .28'i geri alabilmek için exclusion'ı temizle
#   ./rollback.sh unlock          Rolling restart kilitlerini (allocation/rebalance) aç
#   ./rollback.sh settings <dosya> artifacts/ altındaki bir ayar dump'ını geri yükle
#
# Tam veri geri yüklemesi (R6) ayrı script: ./restore-from-backup.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

usage() { sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }
[[ $# -ge 1 ]] || usage

ACTION="$1"; shift || true
preamble

ank_names() { printf '%s,%s,%s' "${ANK_MASTER_1_NAME}" "${ANK_DATA_1_NAME}" "${ANK_DATA_2_NAME}"; }

case "${ACTION}" in

# ---------------------------------------------------------------------------
status)
  section "Mevcut durum"
  es GET '/_cluster/health' | jq '{status, number_of_nodes, active_shards, relocating_shards, unassigned_shards}'
  echo
  log "Migration ile ilgili persistent ayarlar:"
  es GET '/_cluster/settings?flat_settings=true' \
    | jq '.persistent | with_entries(select(.key | test("routing|recovery|awareness")))'
  echo
  log "Voting config exclusions:"
  es GET '/_cluster/state/metadata?filter_path=metadata.cluster_coordination' | jq '.'
  echo
  es GET '/_cat/nodes?v&h=name,ip,node.role,master&s=name'
  echo
  log "Kayıtlı ayar yedekleri:"
  ls -1t "${ARTIFACT_DIR}"/cluster-settings.*.json 2>/dev/null | head -10 || echo "  (yok)"
  log "Son yedek:"
  if [[ -f "${ARTIFACT_DIR}/LAST_BACKUP" ]]; then
    sed 's/^/  /' "${ARTIFACT_DIR}/LAST_BACKUP"
  else
    echo "  (yok — ./02-backup.sh çalıştırılmamış)"
  fi
  ;;

# ---------------------------------------------------------------------------
awareness)
  section "R2 — Awareness kapatılıyor"
  cat <<EOF
  Yapılacak: awareness.attributes ve force.${AWARENESS_ATTR}.values silinecek.

  ETKİSİ: "her shard'ın bir kopyası diğer lokasyonda olsun" zorunluluğu kalkar.
  ES shard'ları serbestçe dengeler; Ankara node'ları hâlâ cluster'daysa veri
  orada KALMAYA DEVAM EDEBİLİR (silinmez). Ankara'yı tamamen boşaltmak için
  ardından './rollback.sh drain-ankara' çalıştırın.

  VERİ KAYBI YOK — bu sadece bir yerleştirme kuralıdır.
EOF
  confirm "Awareness kapatılsın mı?"
  snapshot_cluster_settings "before-rollback-awareness" >/dev/null
  es PUT /_cluster/settings -d "$(jq -nc \
      --arg key "cluster.routing.allocation.awareness.force.${AWARENESS_ATTR}.values" \
      '{persistent: ({"cluster.routing.allocation.awareness.attributes": null} + {($key): null})}')" \
    | jq '.persistent'
  ok "Awareness kapatıldı."
  wait_for_green 3600 || warn "Green'e ulaşılamadı — ./05-watch-relocation.sh ile izleyin."
  ;;

# ---------------------------------------------------------------------------
throttle)
  section "R3 — Recovery throttle"
  echo "Mevcut:"
  es GET '/_cluster/settings?flat_settings=true' \
    | jq '.persistent | with_entries(select(.key | test("recovery|concurrent")))'
  echo
  echo "  1) Durdur   — throttle'ı 1mb'ye çek, WAN trafiğini fiilen kes (acil durum)"
  echo "  2) Sıfırla  — ES default'una dön (40mb)"
  echo "  3) Yeniden ayarla — hosts.env'deki değeri uygula (${RECOVERY_MAX_BYTES_PER_SEC})"
  read -rp "Seçim [1/2/3]: " CH
  case "${CH}" in
    1) BODY='{"persistent":{"indices.recovery.max_bytes_per_sec":"1mb","cluster.routing.allocation.node_concurrent_recoveries":"1"}}' ;;
    2) BODY='{"persistent":{"indices.recovery.max_bytes_per_sec":null,"cluster.routing.allocation.node_concurrent_recoveries":null}}' ;;
    3) BODY="$(jq -nc --arg b "${RECOVERY_MAX_BYTES_PER_SEC}" --arg c "${NODE_CONCURRENT_RECOVERIES}" \
         '{persistent:{"indices.recovery.max_bytes_per_sec":$b,"cluster.routing.allocation.node_concurrent_recoveries":$c}}')" ;;
    *) die "Geçersiz seçim." ;;
  esac
  es PUT /_cluster/settings -d "${BODY}" | jq '.persistent'
  ok "Uygulandı."
  ;;

# ---------------------------------------------------------------------------
drain-ankara)
  section "R1 — Ankara node'ları boşaltılıyor"
  cat <<EOF
  Sıra:
    1. Awareness kapatılır (açıksa) — yoksa shard'lar İstanbul'da toplanamaz
    2. allocation.exclude._name = $(ank_names)
    3. ES tüm shard'ları İstanbul'a taşır (WAN üzerinden geri okuma)
    4. green olunca Ankara node'larını KAPATABİLİRSİNİZ
    5. exclude listesi temizlenir

  VERİ KAYBI YOK — İstanbul'da tam kopya kalır.
  SÜRE: Ankara'ya taşınmış veri miktarı kadar geri taşıma yapılır.
EOF
  confirm "Ankara boşaltılsın mı?"
  snapshot_cluster_settings "before-drain-ankara" >/dev/null

  log "1/5 Awareness kapatılıyor…"
  es PUT /_cluster/settings -d "$(jq -nc \
      --arg key "cluster.routing.allocation.awareness.force.${AWARENESS_ATTR}.values" \
      '{persistent: ({"cluster.routing.allocation.awareness.attributes": null} + {($key): null})}')" >/dev/null
  ok "Awareness kapalı."

  log "2/5 Ankara node'ları allocation dışına alınıyor…"
  es PUT /_cluster/settings -d "$(jq -nc --arg n "$(ank_names)" \
      '{persistent:{"cluster.routing.allocation.exclude._name": $n}}')" >/dev/null
  ok "exclude._name=$(ank_names)"

  log "3/5 Shard'ların İstanbul'a dönmesi bekleniyor…"
  wait_for_green 21600 || die "Green olmadı. ./05-watch-relocation.sh ile izleyin, tamamlanınca bu script'i tekrar çalıştırın."

  REMAIN="$(es GET '/_cat/shards?format=json&h=node' \
    | jq -r --argjson racks "$(node_rack_map)" --arg z "${ZONE_ANK}" \
        '[.[] | select(.node != null and ($racks[.node] // "") == $z)] | length')"
  log "Ankara'da kalan shard: ${REMAIN}"
  [[ "${REMAIN}" == "0" ]] || die "Ankara hâlâ ${REMAIN} shard taşıyor. Node'ları KAPATMAYIN."
  ok "Ankara tamamen boş."

  echo
  echo "  4/5 ŞİMDİ Ankara node'larında ES servislerini durdurabilirsiniz:"
  echo "        ${ANK_MASTER_1_NAME} (${ANK_MASTER_1_IP})"
  echo "        ${ANK_DATA_1_NAME} (${ANK_DATA_1_IP})"
  echo "        ${ANK_DATA_2_NAME} (${ANK_DATA_2_IP})"
  confirm "Ankara node'ları durduruldu mu?"

  log "5/5 exclude listesi temizleniyor…"
  es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.exclude._name":null}}' >/dev/null
  ok "Temizlendi."
  wait_for_green 900
  es GET '/_cat/nodes?v&h=name,ip,node.role&s=name'
  ok "Cluster migration öncesi topolojiye döndü."
  ;;

# ---------------------------------------------------------------------------
readd-master3)
  section "R4 — ${IST_MASTER_3_NAME} geri alınıyor"
  cat <<EOF
  Ön koşul: node'un path.data'sı (/data/elasticsearch-master) SİLİNMEMİŞ olmalı.
  Silinmişse node'u boş olarak da geri alabilirsiniz — master node'unda veri
  tutulmaz, sadece cluster'a yeniden katılır.
EOF
  confirm "voting_config_exclusions temizlensin mi?"
  es DELETE '/_cluster/voting_config_exclusions' >/dev/null
  ok "Exclusion listesi temizlendi."
  cat <<EOF

  ŞİMDİ ${IST_MASTER_3_NAME} (${IST_MASTER_3_IP}) üzerinde:
    1. yaml'da discovery.seed_hosts'un canlı master'ları içerdiğini doğrulayın
    2. cluster.initial_master_nodes SATIRI OLMAMALI
    3. systemctl start elasticsearch
EOF
  confirm "Node başlatıldı mı?"
  for i in $(seq 1 30); do
    if es GET '/_cat/nodes?format=json&h=name' \
       | jq -e --arg n "${IST_MASTER_3_NAME}" 'map(.name)|index($n)!=null' >/dev/null 2>&1; then
      ok "${IST_MASTER_3_NAME} geri katıldı."; break
    fi
    printf '%s  … (%s/30)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
    if (( i == 30 )); then die "Node geri katılmadı. Loglara bakın."; fi
  done
  es GET '/_cat/nodes?v&h=name,ip,node.role,master&s=name'
  ;;

# ---------------------------------------------------------------------------
unlock)
  section "Rolling restart kilitleri açılıyor"
  es PUT /_cluster/settings -d '{"persistent":{
      "cluster.routing.allocation.enable": null,
      "cluster.routing.rebalance.enable": null
    }}' | jq '.persistent'
  ok "allocation.enable ve rebalance.enable default'a döndü."
  ;;

# ---------------------------------------------------------------------------
settings)
  FILE="${1:-}"
  [[ -n "${FILE}" && -f "${FILE}" ]] || {
    fail "Dosya belirtin. Mevcut yedekler:"
    ls -1t "${ARTIFACT_DIR}"/cluster-settings.*.json 2>/dev/null || echo "  (yok)"
    exit 1
  }
  section "Ayar yedeği geri yükleniyor: ${FILE}"
  echo "Geri yüklenecek persistent ayarlar:"
  jq '.persistent' "${FILE}"
  cat <<'EOF'

  DİKKAT: Bu işlem yedekteki ayarları YAZAR, ama yedekten SONRA eklenmiş
  ayarları SİLMEZ. Tam temizlik için önce ilgili anahtarları null'layın.
EOF
  confirm "Uygulansın mı?"
  snapshot_cluster_settings "before-settings-restore" >/dev/null
  es PUT /_cluster/settings -d "$(jq -c '{persistent: .persistent}' "${FILE}")" | jq '.persistent'
  ok "Uygulandı."
  ;;

*) usage ;;
esac
