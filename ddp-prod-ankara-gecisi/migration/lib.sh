#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Ortak yardımcılar. Doğrudan çalıştırılmaz, diğer script'ler source eder.
# ---------------------------------------------------------------------------

set -Eeuo pipefail

_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=hosts.env
source "${_LIB_DIR}/hosts.env"

mkdir -p "${ARTIFACT_DIR}"

# --- Çıktı -----------------------------------------------------------------
if [[ -t 1 ]]; then
  _C_RED=$'\033[31m'; _C_GRN=$'\033[32m'; _C_YLW=$'\033[33m'
  _C_BLU=$'\033[34m'; _C_DIM=$'\033[2m';  _C_OFF=$'\033[0m'
else
  _C_RED=; _C_GRN=; _C_YLW=; _C_BLU=; _C_DIM=; _C_OFF=
fi

log()   { printf '%s[ %s ]%s %s\n' "$_C_BLU" "$(date +%H:%M:%S)" "$_C_OFF" "$*"; }
ok()    { printf '%s  OK  %s %s\n' "$_C_GRN" "$_C_OFF" "$*"; }
warn()  { printf '%s WARN %s %s\n' "$_C_YLW" "$_C_OFF" "$*" >&2; }
fail()  { printf '%s FAIL %s %s\n' "$_C_RED" "$_C_OFF" "$*" >&2; }
die()   { fail "$*"; exit 1; }
hr()    { printf '%s%s%s\n' "$_C_DIM" "$(printf '%.0s-' {1..76})" "$_C_OFF"; }

section() { echo; hr; printf '%s\n' "$*"; hr; }

# Yıkıcı / geri dönüşü zor adımlar öncesi onay. AUTO_CONFIRM=1 ile atlanır.
confirm() {
  local prompt="$1"
  if [[ "${AUTO_CONFIRM:-0}" == "1" ]]; then
    warn "AUTO_CONFIRM=1 — onay atlandı: ${prompt}"
    return 0
  fi
  local answer
  read -rp "${_C_YLW}?${_C_OFF} ${prompt} [evet/hayir]: " answer
  [[ "$answer" == "evet" ]] || die "Kullanıcı onaylamadı, çıkılıyor."
}

# --- Elasticsearch HTTP ----------------------------------------------------
# Kullanım: es GET /_cluster/health
#           es PUT  /_cluster/settings -d '{...}'
# Body stdout'a yazılır; 2xx değilse fonksiyon hata döner.
es() {
  local method="$1" path="$2"; shift 2
  local raw code body
  raw="$(curl -sS --connect-timeout 10 --max-time "${ES_TIMEOUT:-120}" \
              -u "${ES_USER}:${ES_PASSWORD}" \
              -H 'Content-Type: application/json' \
              -w $'\n%{http_code}' \
              -X "${method}" "${ES_URL}${path}" "$@")" || {
    fail "curl başarısız: ${method} ${path}"
    return 1
  }
  code="${raw##*$'\n'}"
  body="${raw%$'\n'*}"
  printf '%s' "${body}"
  if [[ ! "${code}" =~ ^2 ]]; then
    printf '\n' >&2
    fail "${method} ${path} -> HTTP ${code}"
    return 1
  fi
}

# jq ile alan çekmek için kısayol: es_get /_cluster/health '.status'
es_get() { es GET "$1" | jq -r "$2"; }

# --- Ön koşullar -----------------------------------------------------------
require_tools() {
  (( BASH_VERSINFO[0] >= 4 )) \
    || die "bash ${BASH_VERSION} çok eski (>= 4 gerekli). macOS'ta: brew install bash && /opt/homebrew/bin/bash $0"
  local missing=()
  for t in curl jq; do command -v "$t" >/dev/null 2>&1 || missing+=("$t"); done
  ((${#missing[@]} == 0)) || die "Eksik araç(lar): ${missing[*]}"
}

require_credentials() {
  [[ -n "${ES_USER}"     ]] || die "ES_USER tanımlı değil. 'export ES_USER=elastic'"
  [[ -n "${ES_PASSWORD}" ]] || die "ES_PASSWORD tanımlı değil. 'read -rsp \"pw: \" ES_PASSWORD && export ES_PASSWORD'"
}

require_ank_ips() {
  local unset_vars=()
  for v in ANK_MASTER_1_IP ANK_DATA_1_IP ANK_DATA_2_IP; do
    [[ "${!v}" == *"<<"* ]] && unset_vars+=("$v")
  done
  ((${#unset_vars[@]} == 0)) || die "hosts.env içinde doldurulmamış placeholder: ${unset_vars[*]}"
}

require_reachable() {
  es GET /_cluster/health >/dev/null \
    || die "Cluster'a ulaşılamıyor: ${ES_URL} (ES_URL / kimlik bilgisi / ağ kontrol edin)"
}

# Standart açılış: her script'in başında çağrılır.
preamble() {
  require_tools
  require_credentials
  require_reachable
  local actual
  actual="$(es_get /_cluster/health '.cluster_name')"
  [[ "${actual}" == "${CLUSTER_NAME}" ]] \
    || die "Yanlış cluster! Beklenen '${CLUSTER_NAME}', bağlanılan '${actual}'. ES_URL'i kontrol edin."
}

# --- Cluster durum yardımcıları -------------------------------------------
cluster_status()       { es_get /_cluster/health '.status'; }
node_count()           { es_get /_cluster/health '.number_of_nodes'; }
unassigned_shards()    { es_get /_cluster/health '.unassigned_shards'; }
relocating_shards()    { es_get /_cluster/health '.relocating_shards'; }

master_eligible_nodes() {
  es GET '/_cat/nodes?format=json&h=name,node.role' \
    | jq -r '.[] | select(."node.role" | test("m")) | .name' | sort
}

# node adı -> rack attribute eşlemesi (JSON obje olarak stdout'a)
node_rack_map() {
  es GET "/_cat/nodeattrs?format=json&h=node,attr,value" \
    | jq --arg attr "${AWARENESS_ATTR}" \
        '[ .[] | select(.attr == $attr) | {key: .node, value: .value} ] | from_entries'
}

# green olana kadar bekle. wait_for_green <saniye>
wait_for_green() {
  local timeout="${1:-1800}" waited=0 interval=15 st
  log "Cluster'ın green olması bekleniyor (timeout ${timeout}s)…"
  while (( waited < timeout )); do
    st="$(cluster_status)"
    if [[ "${st}" == "green" ]]; then ok "Cluster green (${waited}s)"; return 0; fi
    printf '%s  … status=%s unassigned=%s relocating=%s (%ss)%s\n' \
      "$_C_DIM" "${st}" "$(unassigned_shards)" "$(relocating_shards)" "${waited}" "$_C_OFF"
    sleep "${interval}"; waited=$(( waited + interval ))
  done
  fail "Timeout: cluster ${timeout}s içinde green olmadı (son status=${st})"
  warn "Teşhis için:  curl -u \$ES_USER:\$ES_PASSWORD '${ES_URL}/_cluster/allocation/explain?pretty'"
  return 1
}

# Mevcut persistent+transient ayarları zaman damgalı dosyaya yedekler.
snapshot_cluster_settings() {
  local tag="${1:-settings}"
  local f="${ARTIFACT_DIR}/cluster-settings.${tag}.$(date +%Y%m%d-%H%M%S).json"
  es GET '/_cluster/settings?flat_settings=true' | jq '.' > "${f}"
  ok "Cluster ayarları yedeklendi: ${f}"
  printf '%s' "${f}"
}
