#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 0 — Preflight
#
# Cluster'a HİÇBİR değişiklik yapmaz. Sadece okur ve rapor üretir.
# Migration'a başlamadan önce buradaki tüm FAIL'ler giderilmiş olmalı.
#
#   ./00-preflight.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

REPORT="${ARTIFACT_DIR}/preflight-$(date +%Y%m%d-%H%M%S).txt"
FAILURES=0
WARNINGS=0

check_fail() { fail "$*"; FAILURES=$((FAILURES + 1)); }
check_warn() { warn "$*"; WARNINGS=$((WARNINGS + 1)); }

exec > >(tee "${REPORT}") 2>&1

section "FAZ 0 — PREFLIGHT  ($(date '+%Y-%m-%d %H:%M:%S'))"
log "Endpoint : ${ES_URL}"
log "Rapor    : ${REPORT}"

require_tools
require_credentials
require_ank_ips
require_reachable
preamble
ok "Cluster erişilebilir ve adı doğru: ${CLUSTER_NAME}"

# --- 1. Cluster sağlığı ----------------------------------------------------
section "1. Cluster sağlığı"
HEALTH="$(es GET /_cluster/health)"
STATUS="$(jq -r '.status' <<<"${HEALTH}")"
jq -r '"status=\(.status) nodes=\(.number_of_nodes) data_nodes=\(.number_of_data_nodes) '\
'shards=\(.active_shards) unassigned=\(.unassigned_shards) relocating=\(.relocating_shards)"' <<<"${HEALTH}"

if [[ "${STATUS}" == "green" ]]; then
  ok "Cluster green — migration'a başlamak için gerekli ön koşul sağlandı."
else
  check_fail "Cluster '${STATUS}'. Migration'a SADECE green durumda başlanır. Önce mevcut sorunu çözün."
fi

# --- 2. Node envanteri -----------------------------------------------------
section "2. Node envanteri"
es GET '/_cat/nodes?v&h=name,ip,node.role,master,version,heap.percent,disk.used_percent&s=name'
echo

ACTUAL_NODES="$(es GET '/_cat/nodes?format=json&h=name' | jq -r '.[].name' | sort)"
EXPECTED_NODES="$(printf '%s\n' "${NODES_PHASE0[@]}" | sort)"
if [[ "${ACTUAL_NODES}" == "${EXPECTED_NODES}" ]]; then
  ok "5 IST node'unun tamamı bekleniyor gibi ayakta."
else
  check_fail "Node listesi beklenenden farklı. hosts.env'i gerçek duruma göre düzeltin."
  echo "--- beklenen ---"; printf '%s\n' "${EXPECTED_NODES}"
  echo "--- gerçek   ---"; printf '%s\n' "${ACTUAL_NODES}"
fi

MASTERS="$(master_eligible_nodes)"
MASTER_N="$(wc -l <<<"${MASTERS}" | tr -d ' ')"
log "Master-eligible (${MASTER_N}): $(tr '\n' ' ' <<<"${MASTERS}")"
(( MASTER_N == 3 )) || check_warn "Beklenen 3 master-eligible node, bulunan ${MASTER_N}."

# --- 3. Versiyon tutarlılığı ----------------------------------------------
section "3. Elasticsearch versiyonu"
VERSIONS="$(es GET '/_cat/nodes?format=json&h=version' | jq -r '.[].version' | sort -u)"
printf 'Cluster genelinde bulunan versiyon(lar): %s\n' "$(tr '\n' ' ' <<<"${VERSIONS}")"
if [[ "$(wc -l <<<"${VERSIONS}" | tr -d ' ')" == "1" ]]; then
  ok "Tüm node'lar aynı versiyonda: ${VERSIONS}"
  echo
  echo ">> ANKARA node'larına TAM OLARAK bu versiyon kurulmalı: ${VERSIONS}"
  echo "   Daha eski bir sürüm cluster'a KATILAMAZ. Daha yeni bir minor sürüm katılır"
  echo "   ama cluster'ı fiilen yarı-upgrade duruma sokar — migration ile upgrade'i"
  echo "   aynı anda yapmayın."
else
  check_fail "Node'lar arasında versiyon farkı var. Önce sürümleri eşitleyin."
fi

# --- 4. Yedekleme yöntemi (soğuk kopya ön koşulu) -------------------------
section "4. Yedekleme — soğuk kopya alınabilir mi?"
cat <<EOF
Ortamda paylaşımlı disk (NFS) kurulamadığı için ES snapshot'ı yerine SOĞUK KOPYA
kullanıyoruz: bir data node durdurulup veri klasörü tar'lanacak.

Bu ancak o node TÜM shard'ların birer kopyasını taşıyorsa tam yedek olur.
Aşağıda bu doğrulanıyor — varsayıma güvenmiyoruz.
EOF
echo

SH="$(es GET '/_cat/shards?format=json&h=index,shard,prirep,state,node&expand_wildcards=open')"
TOTAL_SH="$(jq -r '[.[] | select(.state=="STARTED") | "\(.index)#\(.shard)"] | unique | length' <<<"${SH}")"
log "Cluster'daki farklı shard sayısı: ${TOTAL_SH}"

FULL_COPY_NODES=""
while IFS= read -r dn; do
  [[ -n "${dn}" ]] || continue
  cnt="$(jq -r --arg n "${dn}" '[.[] | select(.state=="STARTED" and .node==$n) | "\(.index)#\(.shard)"] | unique | length' <<<"${SH}")"
  if [[ "${cnt}" == "${TOTAL_SH}" ]]; then
    ok "${dn}: ${cnt}/${TOTAL_SH} shard — TAM KOPYA taşıyor"
    FULL_COPY_NODES="${FULL_COPY_NODES} ${dn}"
  else
    echo "    ${dn}: ${cnt}/${TOTAL_SH} shard (tam değil)"
  fi
done < <(jq -r '[.[] | select(.node != null and .node != "") | .node] | unique | .[]' <<<"${SH}")

if [[ -n "${FULL_COPY_NODES}" ]]; then
  ok "Soğuk kopya alınabilir. Aday node(lar):${FULL_COPY_NODES}"
  echo "    hosts.env -> BACKUP_NODE_NAME=${BACKUP_NODE_NAME}"
  grep -qw "${BACKUP_NODE_NAME}" <<<"${FULL_COPY_NODES}" \
    || check_fail "Seçili BACKUP_NODE_NAME=${BACKUP_NODE_NAME} tam kopya taşımıyor! hosts.env'i yukarıdaki adaylardan biriyle değiştirin."
else
  check_fail "Hiçbir data node tüm shard'ları taşımıyor — tek node'un kopyası EKSİK yedek olur."
  echo "    Seçenekler: (a) tüm data node'ların kopyasını alın, (b) NFS/S3 bulup gerçek snapshot'a geçin"
fi

echo
log "path.repo durumu (bilgi — soğuk kopya için gerekmiyor):"
es GET '/_nodes/settings' \
  | jq -r '.nodes[] | "  \(.name)\t\(.settings.path.repo // "tanımsız" | tostring)"' | sort
echo "    Not: ileride NFS/S3 bulunursa gerçek snapshot'a geçilebilir; config"
echo "    dosyalarında path.repo satırları hazır duruyor."

# --- 5. cluster.initial_master_nodes --------------------------------------
section "5. cluster.initial_master_nodes"
IMN="$(es GET '/_nodes/settings' \
  | jq -r '[.nodes[] | select(.settings.cluster.initial_master_nodes != null) | .name] | join(", ")')"
if [[ -n "${IMN}" ]]; then
  check_warn "Hâlâ initial_master_nodes taşıyan node'lar: ${IMN}"
  cat <<'EOF'
  Bu ayar cluster bootstrap edildikten sonra göz ardı edilir, yani ŞU AN zararsız.
  Ama bir node'un path.data'sı kaybolursa o node kendi başına YENİ bir cluster
  bootstrap eder ve split-brain benzeri bir duruma yol açar. Faz 0 rolling
  restart'ında yorum satırına alınacak.
EOF
else
  ok "Hiçbir node'da initial_master_nodes yok."
fi

# --- 6. Replica denetimi (awareness'in ön koşulu) -------------------------
section "6. Index replica denetimi"
IDX="$(es GET '/_cat/indices?format=json&h=index,health,pri,rep,docs.count,pri.store.size,store.size&expand_wildcards=open,closed')"

ZERO_REP="$(jq -r '[.[] | select((.rep|tonumber) == 0) | .index] | .[]' <<<"${IDX}" || true)"
MULTI_REP="$(jq -r '[.[] | select((.rep|tonumber) > 1) | "\(.index) (rep=\(.rep))"] | .[]' <<<"${IDX}" || true)"

if [[ -n "${ZERO_REP}" ]]; then
  check_warn "number_of_replicas=0 olan index'ler — bunlar ANKARA'ya HİÇ KOPYALANMAZ:"
  printf '    %s\n' ${ZERO_REP}
  echo "    Awareness replica üretmez, sadece var olan kopyaları dağıtır."
  echo "    ANK'ta da bulunmaları isteniyorsa migration ÖNCESİ rep=1 yapın:"
  echo "      PUT /<index>/_settings {\"index.number_of_replicas\": 1}"
else
  ok "rep=0 olan index yok."
fi

if [[ -n "${MULTI_REP}" ]]; then
  check_warn "number_of_replicas>1 olan index'ler — 2 zone'lu forced awareness ile KALICI YELLOW kalır:"
  printf '    %s\n' ${MULTI_REP}
  echo "    2 zone = shard başına 2 kopya. Fazla replica'lar unassigned kalır."
  echo "    Doğru değer 1: PUT /<index>/_settings {\"index.number_of_replicas\": 1}"
else
  ok "rep>1 olan index yok."
fi

echo
es GET '/_cat/indices?v&h=index,health,pri,rep,docs.count,pri.store.size,store.size&s=index&expand_wildcards=open,closed'

# --- 7. Veri hacmi ve WAN transfer tahmini --------------------------------
section "7. Veri hacmi / transfer tahmini"
es GET '/_cat/allocation?v&h=node,shards,disk.indices,disk.used,disk.avail,disk.total,disk.percent&s=node'
echo

PRI_BYTES="$(es GET '/_cat/indices?format=json&bytes=b&h=pri.store.size&expand_wildcards=open,closed' \
  | jq '[.[]["pri.store.size"] // "0" | tonumber] | add // 0')"
THROTTLE_MB="${RECOVERY_MAX_BYTES_PER_SEC%mb}"
if [[ "${PRI_BYTES}" -gt 0 && "${THROTTLE_MB}" =~ ^[0-9]+$ ]]; then
  awk -v b="${PRI_BYTES}" -v mb="${THROTTLE_MB}" -v conc="${NODE_CONCURRENT_RECOVERIES}" 'BEGIN{
    gb = b/1024/1024/1024;
    secs = (b/1024/1024) / (mb*conc);
    printf "Ankara\x27ya kopyalanacak veri  : %.2f GB (primary\x27lerin bir tam kopyası)\n", gb;
    printf "Throttle                      : %s MB/s x %s eşzamanlı recovery\n", mb, conc;
    printf "Kaba tahmini süre             : %.1f saat (ideal koşulda; WAN kapasitesi sınırlayıcıysa daha uzun)\n", secs/3600;
  }'
  echo
  echo "NOT: Bu tahmin ES throttle'ını baz alır. Gerçek süre IST<->ANK hat kapasitesine"
  echo "     bağlıdır. Hat 100Mbps ise ~12MB/s'yi geçemezsiniz; throttle'ı ona göre kısın."
else
  check_warn "Veri hacmi hesaplanamadı."
fi

# --- 8. Ankara ağ erişilebilirliği ----------------------------------------
section "8. Ankara ağ erişilebilirliği"
echo "Bu makineden Ankara node'larına erişim denenecek (gerçek gereksinim node-to-node,"
echo "aşağıdaki manuel komutları da MUTLAKA çalıştırın):"
echo
for pair in "${ANK_MASTER_1_NAME}:${ANK_MASTER_1_IP}" \
            "${ANK_DATA_1_NAME}:${ANK_DATA_1_IP}" \
            "${ANK_DATA_2_NAME}:${ANK_DATA_2_IP}"; do
  name="${pair%%:*}"; ip="${pair##*:}"
  for port in 9200 9300; do
    if command -v nc >/dev/null 2>&1 && nc -z -w 3 "${ip}" "${port}" 2>/dev/null; then
      ok "${name} (${ip}:${port}) açık"
    else
      check_warn "${name} (${ip}:${port}) kapalı/erişilemiyor (node henüz kurulu değilse normal)"
    fi
  done
done

if command -v ping >/dev/null 2>&1; then
  echo
  log "IST -> ANK RTT ölçümü (${ANK_MASTER_1_IP}):"
  ping -c 5 "${ANK_MASTER_1_IP}" 2>/dev/null | tail -2 || check_warn "ping başarısız"
  echo "  >> RTT > 100ms ise stretched cluster yanlış araçtır; CCR (cross-cluster"
  echo "     replication) değerlendirilmelidir. 100ms üstünde her yazma işlemi"
  echo "     replica ANK'ta olduğu için bu gecikmeyi yer."
fi

cat <<EOF

  HER NODE ÜZERİNDE ÇALIŞTIRILACAK (9300 çift yönlü olmalı — sadece master'lar
  değil, 7 node'un HEPSİ birbirini görmeli; shard recovery data<->data konuşur):

    # İstanbul node'larından:
    for ip in ${ANK_MASTER_1_IP} ${ANK_DATA_1_IP} ${ANK_DATA_2_IP}; do
      nc -z -w 3 \$ip 9300 && echo "OK  \$ip:9300" || echo "FAIL \$ip:9300"
    done

    # Ankara node'larından:
    for ip in ${IST_MASTER_1_IP} ${IST_MASTER_2_IP} ${IST_DATA_4_IP} ${IST_DATA_5_IP}; do
      nc -z -w 3 \$ip 9300 && echo "OK  \$ip:9300" || echo "FAIL \$ip:9300"
    done
EOF

# --- 9. Sertifika hatırlatması --------------------------------------------
section "9. Transport TLS"
cat <<'EOF'
Cluster'da xpack.security.transport.ssl.enabled: true ve verification_mode:
certificate ayarlı. Ankara node'ları AYNI CA'dan üretilmiş sertifika kullanmazsa
cluster'a KATILAMAZ — ve hata mesajı genelde "handshake failed" gibi belirsizdir.

  1. Mevcut bir node'dan kopyalayın:
       scp <ist-node>:/app/elasticsearch-*/config/certs/elastic-certificates.p12 \
           <ank-node>:/app/elasticsearch-*/config/certs/
       chown elasticsearch:elasticsearch .../elastic-certificates.p12
       chmod 640 .../elastic-certificates.p12

  2. Keystore parolasını ANK node'larında da tanımlayın:
       bin/elasticsearch-keystore add xpack.security.transport.ssl.keystore.secure_password
       bin/elasticsearch-keystore add xpack.security.transport.ssl.truststore.secure_password
     (parola IST node'larındakiyle aynı olmalı; boş parola kullanılıyorsa bu adım atlanır)

verification_mode: certificate olduğu için sadece CA zinciri doğrulanır, hostname/SAN
doğrulanmaz — yeni IP'ler için sertifika yeniden üretmeye GEREK YOK, kopyalamak yeterli.
EOF

# --- 10. İstemci envanteri -------------------------------------------------
section "10. ES'e bağlanan istemciler"
cat <<EOF
${IST_MASTER_3_NAME} (${IST_MASTER_3_IP}) decommission edilecek. Bu IP'ye 9200'den
bağlanan her istemci migration sonrası kırılır. Aşağıdaki repo'larda arayın:

  grep -rn "${IST_MASTER_3_IP}" ../../../index-api ../../../search-api ../../../ui ../../../cms-catalog

AYRI BİR KONU (migration kapsamı dışı, ama not edilmeli):
reindex.remote.whitelist: 10.6.245.96:9200 SADECE ${IST_MASTER_1_NAME} üzerinde
tanımlı, diğer node'larda yok. Migration'ı etkilemez, ama reindex-from-remote
isteği başka bir node'a düşerse sessizce başarısız olur. Bilerek mi böyle,
karar sizin — migration config'lerinde DEĞİŞTİRİLMEDİ.
EOF

# --- Özet ------------------------------------------------------------------
section "ÖZET"
printf 'FAIL : %d\nWARN : %d\n\n' "${FAILURES}" "${WARNINGS}"
if (( FAILURES > 0 )); then
  fail "Preflight BAŞARISIZ. Yukarıdaki FAIL maddeleri giderilmeden migration'a başlamayın."
  exit 1
fi
ok "Preflight geçti. Sıradaki adım: 01-rolling-restart.sh (path.repo + final seed_hosts)"
if (( WARNINGS > 0 )); then
  warn "${WARNINGS} uyarı var — devam etmeden önce her birini bilinçli olarak kabul edin."
fi
