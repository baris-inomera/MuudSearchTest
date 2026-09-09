#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 0 — YEDEK ALMA (soğuk kopya)  ·  migration'ın geri dönüş noktası
#
# Bu script SUCCESS vermeden Faz 1'e GEÇİLMEZ.
#
# NEDEN SNAPSHOT DEĞİL: ES snapshot'ı paylaşımlı bir disk (path.repo) ister,
# ortamda NFS kurulamıyor. Onun yerine bir data node'un veri klasörünü soğuk
# kopyalıyoruz.
#
# NEDEN ÇALIŞIYOR: 5 index x 1 shard x rep=1 = 10 shard kopyası, 2 data node.
# ES primary ile replica'sını aynı node'a koymaz -> her data node, tüm index'lerin
# birer kopyasını taşır. Tek node'un klasörü = tam veri seti.
# Script bu varsayımı her çalıştığında YENİDEN DOĞRULAR (bkz. adım 3).
#
# Ne yedeklenir:
#   1. Veri            -> BACKUP_NODE'un path.data klasörü (tar)
#   2. Cluster ayarları, mapping, template, ILM, alias  -> artifacts/
#   3. Doküman sayısı (bütünlük referansı)              -> artifacts/
#   4. Node yaml + keystore                             -> operatör, elle
#
#   ./02-backup.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
section "FAZ 0 — YEDEK ALMA (soğuk kopya)"

TS="$(date +%Y%m%d-%H%M%S)"
DUMP_DIR="${ARTIFACT_DIR}/backup-${TS}"
TARBALL="${BACKUP_DEST_DIR}/es-data-$(basename "${BACKUP_NODE_NAME}")-${TS}.tar.gz"
mkdir -p "${DUMP_DIR}"
log "Yerel yedek dizini : ${DUMP_DIR}"
log "Kopyalanacak node  : ${BACKUP_NODE_NAME} (${BACKUP_NODE_IP})"
log "Veri klasörü       : ${BACKUP_DATA_PATH}"
log "Hedef tar          : ${TARBALL}"

[[ "$(cluster_status)" == "green" ]] \
  || die "Cluster green değil. Yarım bir cluster'dan alınan kopya güvenilmez."

# --- 1. Metadata dump'ları -------------------------------------------------
section "1. Metadata dump'ları"
dump() {
  local name="$1" path="$2"
  if es GET "${path}" | jq '.' > "${DUMP_DIR}/${name}.json" 2>/dev/null; then
    ok "${name}.json"
  else
    warn "${name} alınamadı (${path}) — bu sürümde olmayabilir"
    rm -f "${DUMP_DIR}/${name}.json"
  fi
}

dump cluster-settings    '/_cluster/settings?flat_settings=true&include_defaults=false'
dump cluster-health      '/_cluster/health?level=indices'
dump nodes-settings      '/_nodes/settings'
dump index-settings      '/_all/_settings?expand_wildcards=all'
dump index-mappings      '/_all/_mapping?expand_wildcards=all'
dump aliases             '/_alias?expand_wildcards=all'
dump index-templates     '/_index_template'
dump legacy-templates    '/_template'
dump component-templates '/_component_template'
dump ilm-policies        '/_ilm/policy'
dump ingest-pipelines    '/_ingest/pipeline'
dump security-roles      '/_security/role'
dump security-rolemap    '/_security/role_mapping'

es GET '/_cat/indices?v&h=index,health,status,pri,rep,docs.count,pri.store.size,store.size&s=index&expand_wildcards=all' > "${DUMP_DIR}/indices.txt"
es GET '/_cat/shards?v&h=index,shard,prirep,state,docs,store,node&s=index,shard&expand_wildcards=all'    > "${DUMP_DIR}/shards.txt"
es GET '/_cat/nodes?v&h=name,ip,node.role,master,version&s=name'                                        > "${DUMP_DIR}/nodes.txt"
ok "indices.txt / shards.txt / nodes.txt"

DOC_TOTAL="$(es GET '/_all/_count?expand_wildcards=all' | jq -r '.count // 0')"
printf '%s\n' "${DOC_TOTAL}" > "${DUMP_DIR}/doc-count.txt"
ok "Toplam doküman: ${DOC_TOTAL}  (yedek sonrası bu sayı DEĞİŞMEMELİ)"

# --- 2. Yazma trafiği ------------------------------------------------------
section "2. Yazma trafiği"
cat <<EOF
  Soğuk kopyanın tutarlı olması için kopyalama sırasında ES'e yazma OLMAMALI.
  Yazan servisler: index-api, cms-catalog (ve varsa cron/batch işleri).
EOF
confirm "ES'e yazan tüm servisler durduruldu mu?"

# --- 3. TAM KOPYA DOĞRULAMASI (varsayımı sınama) --------------------------
section "3. '${BACKUP_NODE_NAME}' tüm veriyi taşıyor mu?"
echo "Bu kontrol atlanamaz: node tüm index'lerin bir kopyasını taşımıyorsa"
echo "alınan yedek EKSİK olur ve bunu ancak restore anında fark edersiniz."
echo

SHARDS_JSON="$(es GET '/_cat/shards?format=json&h=index,shard,prirep,state,node&expand_wildcards=open')"

MISSING="$(jq -r --arg n "${BACKUP_NODE_NAME}" '
  [ .[] | select(.state == "STARTED") ]                       as $all
  | ([ $all[] | select(.node == $n) | "\(.index)#\(.shard)" ] | unique) as $onNode
  | ([ $all[] | "\(.index)#\(.shard)" ] | unique)              as $every
  | ($every - $onNode) | .[]' <<<"${SHARDS_JSON}")"

TOTAL_SHARDS="$(jq -r '[.[] | select(.state=="STARTED") | "\(.index)#\(.shard)"] | unique | length' <<<"${SHARDS_JSON}")"
ON_NODE="$(jq -r --arg n "${BACKUP_NODE_NAME}" '[.[] | select(.state=="STARTED" and .node==$n) | "\(.index)#\(.shard)"] | unique | length' <<<"${SHARDS_JSON}")"

log "Cluster'daki farklı shard sayısı      : ${TOTAL_SHARDS}"
log "${BACKUP_NODE_NAME} üzerindeki kopya  : ${ON_NODE}"

if [[ -n "${MISSING}" ]]; then
  fail "${BACKUP_NODE_NAME} şu shard'ları TAŞIMIYOR:"
  printf '    %s\n' ${MISSING}
  cat <<EOF

  Bu node'un kopyası EKSİK bir yedek olur. Seçenekler:
    a) Diğer data node'u deneyin:  hosts.env -> BACKUP_NODE_NAME=${IST_DATA_4_NAME}
    b) İki data node'un da kopyasını alın (ikisi birlikte tam seti verir)
    c) Index/shard yapısı değişmiş olabilir — yukarıdaki listeyi inceleyin

  Mevcut dağılım:
EOF
  es GET '/_cat/shards?v&h=index,shard,prirep,state,node&s=index,shard'
  die "Yedek alınmadı."
fi
ok "${BACKUP_NODE_NAME} tüm ${TOTAL_SHARDS} shard'ın birer kopyasını taşıyor — tam yedek alınabilir."

# --- 4. Flush --------------------------------------------------------------
section "4. Flush"
echo "Translog'daki her şey diske (Lucene segment'lerine) yazılıyor, böylece"
echo "kopyalanan klasör eksiksiz olur."
es POST '/_flush' >/dev/null 2>&1 && ok "flush tamam" || warn "flush başarısız (graceful shutdown zaten flush eder, devam)"

# --- 5. Allocation kilidi --------------------------------------------------
section "5. Allocation kilidi"
echo "Node kapanınca ES'in panikleyip shard kopyalamaya başlamasını engelliyoruz."
snapshot_cluster_settings "before-coldbackup" >/dev/null
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":"primaries"}}' >/dev/null
ok "cluster.routing.allocation.enable=primaries"

# Script yarıda kalırsa kilidi bırakma.
_unlock() {
  warn "Çıkış yakalandı — allocation kilidi açılıyor…"
  es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":null}}' >/dev/null 2>&1 || true
}
trap _unlock ERR INT TERM

# --- 6. Node'u durdur ------------------------------------------------------
section "6. ${BACKUP_NODE_NAME} durduruluyor"
cat <<EOF
  ŞİMDİ ${BACKUP_NODE_IP} üzerinde:

      systemctl stop elasticsearch

  Cluster YELLOW olacak — normal. Her shard'ın kopyası ${IST_DATA_4_NAME}'de
  duruyor, arama ve okuma çalışmaya devam eder, veri kaybı riski yok.
EOF
confirm "${BACKUP_NODE_NAME} durduruldu mu?"

log "Node'un cluster'dan düşmesi bekleniyor…"
for i in $(seq 1 30); do
  if ! es GET '/_cat/nodes?format=json&h=name' \
       | jq -e --arg n "${BACKUP_NODE_NAME}" 'map(.name) | index($n) != null' >/dev/null 2>&1; then
    ok "Node cluster listesinden düştü."; break
  fi
  printf '%s  … (%s/30)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
  if (( i == 30 )); then die "Node hâlâ listede — servis gerçekten durdu mu?"; fi
done

# --- 7. Kopyalama ----------------------------------------------------------
section "7. Veri klasörünü kopyala"
cat <<EOF
  ŞİMDİ ${BACKUP_NODE_IP} üzerinde (aynı makinede yer varsa):

      mkdir -p ${BACKUP_DEST_DIR}
      tar czf ${TARBALL} -C "$(dirname "${BACKUP_DATA_PATH}")" "$(basename "${BACKUP_DATA_PATH}")"

  Yer yoksa doğrudan başka bir sunucuya:

      tar czf - -C "$(dirname "${BACKUP_DATA_PATH}")" "$(basename "${BACKUP_DATA_PATH}")" \\
        | ssh <hedef-sunucu> 'cat > /yedek/es-data-${BACKUP_NODE_NAME}-${TS}.tar.gz'

  Bittiğinde boyutu kontrol edin — 0 byte veya beklenenden çok küçükse durun:

      ls -lh ${TARBALL}
EOF
confirm "Kopyalama tamamlandı ve tar dosyasının boyutu makul mü?"

# --- 8. Node'u başlat ------------------------------------------------------
section "8. ${BACKUP_NODE_NAME} başlatılıyor"
echo "  ŞİMDİ ${BACKUP_NODE_IP} üzerinde:  systemctl start elasticsearch"
confirm "${BACKUP_NODE_NAME} başlatıldı mı?"

log "Node'un geri katılması bekleniyor…"
for i in $(seq 1 60); do
  if es GET '/_cat/nodes?format=json&h=name' \
     | jq -e --arg n "${BACKUP_NODE_NAME}" 'map(.name) | index($n) != null' >/dev/null 2>&1; then
    ok "Node geri katıldı."; break
  fi
  printf '%s  … (%s/60)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
  if (( i == 60 )); then die "Node geri katılmadı. Loglara bakın:
     tail -200 /app/elasticsearch-data/logs/${CLUSTER_NAME}.log"; fi
done

# --- 9. Kilidi aç ve doğrula ----------------------------------------------
section "9. Kilidi aç ve doğrula"
trap - ERR INT TERM
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":null}}' >/dev/null
ok "allocation.enable=null (default)"
wait_for_green 1800 || die "Cluster green olmadı. ./rollback.sh status ile bakın."

DOC_AFTER="$(es GET '/_all/_count?expand_wildcards=all' | jq -r '.count // 0')"
log "Yedek öncesi doküman : ${DOC_TOTAL}"
log "Yedek sonrası        : ${DOC_AFTER}"
[[ "${DOC_AFTER}" == "${DOC_TOTAL}" ]] \
  && ok "Doküman sayısı aynı." \
  || warn "Fark var. Yazma trafiği gerçekten kapalı mıydı?"

# --- 9b. Master node cluster state kopyası --------------------------------
section "9b. Cluster state kopyası (${IST_MASTER_1_NAME})"
cat <<EOF
  Data node'un klasöründe SHARD VERİSİ var, ama index mapping'leri, ayarları,
  template'ler ve alias'lar CLUSTER STATE içinde — o da master node'larda duruyor.
  Tam geri dönüş için bir master'ın klasörü de lazım. Küçüktür (birkaç MB).

  3 master var, birini durdurmak güvenli (quorum 2, kalan 2 master yeter).
EOF
confirm "Master kopyası da alınsın mı? (hayır derseniz yedek EKSİK olur)"

MASTER_TAR="${BACKUP_DEST_DIR}/es-master-${IST_MASTER_1_NAME}-${TS}.tar.gz"
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":"primaries"}}' >/dev/null
trap _unlock ERR INT TERM

cat <<EOF

  ŞİMDİ ${IST_MASTER_1_IP} üzerinde sırasıyla:

      systemctl stop elasticsearch
      tar czf ${MASTER_TAR} -C /data elasticsearch-master
      systemctl start elasticsearch
EOF
confirm "${IST_MASTER_1_NAME} durduruldu, kopyalandı ve tekrar başlatıldı mı?"

log "Master'ın geri katılması bekleniyor…"
for i in $(seq 1 60); do
  if es GET '/_cat/nodes?format=json&h=name' \
     | jq -e --arg n "${IST_MASTER_1_NAME}" 'map(.name) | index($n) != null' >/dev/null 2>&1; then
    ok "${IST_MASTER_1_NAME} geri katıldı."; break
  fi
  printf '%s  … (%s/60)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
  if (( i == 60 )); then die "Master geri katılmadı. Loglara bakın."; fi
done

trap - ERR INT TERM
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":null}}' >/dev/null
wait_for_green 900 || die "Cluster green olmadı."
ok "Cluster state kopyası alındı."

# --- 10. Node config yedeği (operatör) ------------------------------------
section "10. Node yaml + keystore — OPERATÖR"
cat <<EOF
  Veri kopyası node konfigürasyonunu KURTARMAZ. 5 node'un HEPSİNDE:

    cd /app/elasticsearch-*/config
    cp elasticsearch.yml      elasticsearch.yml.pre-migration-${TS}
    cp elasticsearch.keystore elasticsearch.keystore.pre-migration-${TS}

  keystore önemli — sertifika parolaları orada, kaybolursa node açılmaz.
EOF
confirm "Tüm node'larda yaml + keystore yedeği alındı mı?"

# --- Kayıt -----------------------------------------------------------------
cat > "${ARTIFACT_DIR}/LAST_BACKUP" <<EOF
timestamp=${TS}
data_node=${BACKUP_NODE_NAME}
data_node_ip=${BACKUP_NODE_IP}
data_path=${BACKUP_DATA_PATH}
data_tarball=${TARBALL}
master_node=${IST_MASTER_1_NAME}
master_node_ip=${IST_MASTER_1_IP}
master_tarball=${MASTER_TAR}
doc_count=${DOC_TOTAL}
shards=${TOTAL_SHARDS}
dump_dir=${DUMP_DIR}
EOF

section "YEDEK ÖZETİ"
cat <<EOF
  Shard verisi    : ${TARBALL}
                    (${BACKUP_NODE_IP} üzerinde)
  Cluster state   : ${MASTER_TAR}
                    (${IST_MASTER_1_IP} üzerinde)
  Metadata dump   : ${DUMP_DIR}
  Doküman sayısı  : ${DOC_TOTAL}   ·   Shard: ${TOTAL_SHARDS}

  Geri yükleme    : ./restore-from-backup.sh      (ROLLBACK.md -> R6)

  ÖNEMLİ — iki tar dosyasını da bulundukları makinenin DIŞINA kopyalayın:

      scp ${BACKUP_NODE_IP}:${TARBALL}   <guvenli-yer>/
      scp ${IST_MASTER_1_IP}:${MASTER_TAR} <guvenli-yer>/

  Makine giderse yedek de gider. Yedek, yedeklediği makinede durmaz.

  Yazma trafiğini şimdi geri açabilirsiniz.
EOF
ok "Sıradaki adım: ./03-join-ankara.sh"
