#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 0 — Rolling restart (IST node'larına yeni yaml'ı uygula)
#
# NEDEN: path.repo static bir ayar ve şu an HİÇBİR node'da yok -> snapshot
# alınamıyor. Aynı restart'ta final discovery.seed_hosts'u da yazıyoruz ve
# cluster.initial_master_nodes'u yorum satırına alıyoruz. Böylece migration
# boyunca ikinci bir rolling restart gerekmiyor.
#
# Bu script SSH yapmaz — node'u durdurup başaltma adımını operatör yapar.
# Script tüm ES tarafı işleri (allocation kilidi, bekleme, doğrulama) üstlenir.
#
#   ./01-rolling-restart.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
section "FAZ 0 — ROLLING RESTART (yeni yaml'ın uygulanması)"

[[ "$(cluster_status)" == "green" ]] || die "Cluster green değil. Rolling restart'a green olmadan başlanmaz."
snapshot_cluster_settings "before-rolling-restart" >/dev/null

# Elected master'ı en sona bırak — gereksiz master seçimi tetiklememek için.
ELECTED="$(es GET '/_cat/master?format=json&h=node' | jq -r '.[0].node')"
log "Şu anki elected master: ${ELECTED}"

ORDER=()
for n in "${IST_DATA_4_NAME}" "${IST_DATA_5_NAME}" \
         "${IST_MASTER_3_NAME}" "${IST_MASTER_2_NAME}" "${IST_MASTER_1_NAME}"; do
  [[ "${n}" == "${ELECTED}" ]] || ORDER+=("${n}")
done
ORDER+=("${ELECTED}")

log "Restart sırası: ${ORDER[*]}"

declare -A NODE_IP=(
  ["${IST_MASTER_1_NAME}"]="${IST_MASTER_1_IP}"
  ["${IST_MASTER_2_NAME}"]="${IST_MASTER_2_IP}"
  ["${IST_MASTER_3_NAME}"]="${IST_MASTER_3_IP}"
  ["${IST_DATA_4_NAME}"]="${IST_DATA_4_IP}"
  ["${IST_DATA_5_NAME}"]="${IST_DATA_5_IP}"
)

cat <<EOF

Her node için yapılacaklar (operatör):
  1. Mevcut yaml'ı yedekle:
       cp elasticsearch.yml elasticsearch.yml.bak-\$(date +%Y%m%d-%H%M%S)
  2. config/ dizinindeki ilgili yeni yaml'ı kopyala
  3. ${SNAPSHOT_PATH_REPO} mount'unun var ve elasticsearch kullanıcısına yazılabilir
     olduğunu doğrula:
       sudo -u elasticsearch touch ${SNAPSHOT_PATH_REPO}/.wtest && rm -f ${SNAPSHOT_PATH_REPO}/.wtest
  4. systemctl restart <elasticsearch servisi>

EOF
confirm "Rolling restart'a başlansın mı?"

wait_for_node() {
  local name="$1" timeout="${2:-600}" waited=0
  log "'${name}' node'unun cluster'a geri katılması bekleniyor…"
  while (( waited < timeout )); do
    if es GET '/_cat/nodes?format=json&h=name' 2>/dev/null | jq -e --arg n "${name}" \
         'map(.name) | index($n) != null' >/dev/null 2>&1; then
      ok "'${name}' geri katıldı (${waited}s)"
      return 0
    fi
    printf '%s  … bekleniyor (%ss)%s\n' "$_C_DIM" "${waited}" "$_C_OFF"
    sleep 10; waited=$(( waited + 10 ))
  done
  die "'${name}' ${timeout}s içinde geri katılmadı. Node loglarına bakın:
       tail -200 /app/elasticsearch-*/logs/${CLUSTER_NAME}.log
     Sık görülen neden: yaml'da initial_master_nodes bırakıldı veya seed_hosts yanlış."
}

for NODE in "${ORDER[@]}"; do
  section "Restart: ${NODE} (${NODE_IP[$NODE]})"

  log "Shard allocation 'primaries'e alınıyor (restart sırasında gereksiz shard hareketini önler)…"
  es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":"primaries"}}' >/dev/null
  ok "allocation.enable=primaries"

  log "Translog flush ediliyor (recovery'yi hızlandırır)…"
  es POST '/_flush' >/dev/null 2>&1 || warn "flush başarısız (kritik değil, devam)"

  echo
  echo "  ŞİMDİ ${NODE} üzerinde:"
  echo "    cp elasticsearch.yml elasticsearch.yml.bak-\$(date +%Y%m%d-%H%M%S)"
  echo "    # yeni yaml'ı yerine koy"
  echo "    systemctl restart elasticsearch   # servis adınız farklı olabilir"
  echo
  confirm "${NODE} restart edildi mi?"

  wait_for_node "${NODE}"

  log "Shard allocation geri açılıyor…"
  es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.allocation.enable":null}}' >/dev/null
  ok "allocation.enable=null (default: all)"

  wait_for_green 1800 || die "Cluster green olmadı. SONRAKİ NODE'A GEÇMEYİN. Rollback için ROLLBACK.md R5."
done

# --- Doğrulama -------------------------------------------------------------
section "Rolling restart doğrulaması"

log "path.repo:"
es GET '/_nodes/settings' \
  | jq -r '.nodes[] | "  \(.name)\t\(.settings.path.repo // "TANIMSIZ" | tostring)"' | sort
MISSING="$(es GET '/_nodes/settings' \
  | jq -r '[.nodes[] | select(.settings.path.repo == null) | .name] | join(", ")')"
[[ -z "${MISSING}" ]] || die "path.repo hâlâ eksik: ${MISSING}"
ok "path.repo tüm node'larda tanımlı."

log "discovery.seed_hosts:"
es GET '/_nodes/settings' \
  | jq -r '.nodes[] | "  \(.name)\t\(.settings.discovery.seed_hosts // [] | join(","))"' | sort

log "cluster.initial_master_nodes:"
IMN="$(es GET '/_nodes/settings' \
  | jq -r '[.nodes[] | select(.settings.cluster.initial_master_nodes != null) | .name] | join(", ")')"
if [[ -z "${IMN}" ]]; then
  ok "Hiçbir node'da initial_master_nodes kalmadı."
else
  warn "Hâlâ initial_master_nodes taşıyan node'lar: ${IMN} — yaml'ları kontrol edin."
fi

wait_for_green 600
ok "FAZ 0 tamam. Sıradaki adım: ./02-backup.sh (snapshot alınacak)"
