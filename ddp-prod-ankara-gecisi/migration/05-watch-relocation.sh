#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# İzleme — salt okunur. Cluster'a hiçbir değişiklik yapmaz.
# Faz 2 sırasında ayrı bir terminalde çalıştırın, Ctrl-C ile çıkın.
#
#   ./05-watch-relocation.sh [yenileme-saniyesi]
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
INTERVAL="${1:-15}"

trap 'echo; log "İzleme durduruldu."; exit 0' INT

while true; do
  clear 2>/dev/null || true
  printf '%s  ddp-prod-cluster — relocation izleme   (%s, her %ss)%s\n' \
    "$_C_BLU" "$(date '+%H:%M:%S')" "${INTERVAL}" "$_C_OFF"
  hr

  es GET '/_cluster/health' | jq -r '
    "status          : \(.status)
nodes           : \(.number_of_nodes) (data: \(.number_of_data_nodes))
active shards   : \(.active_shards)  (\(.active_shards_percent_as_number | floor)%)
relocating      : \(.relocating_shards)
initializing    : \(.initializing_shards)
unassigned      : \(.unassigned_shards)"'

  hr
  echo "Node başına dağılım:"
  es GET '/_cat/allocation?v&h=node,shards,disk.indices,disk.used,disk.avail,disk.percent&s=node'

  hr
  echo "Zone başına shard sayısı:"
  RACKS="$(node_rack_map)"
  es GET '/_cat/shards?format=json&h=state,node' \
    | jq -r --argjson racks "${RACKS}" '
        [ .[] | select(.node != null and .node != "") | $racks[.node] // "?" ]
        | group_by(.) | map({zone: .[0], shards: length}) | .[]
        | "  \(.zone)\t\(.shards)"'

  hr
  ACTIVE="$(es GET '/_cat/recovery?format=json&active_only=true' | jq 'length')"
  echo "Aktif recovery: ${ACTIVE}"
  if (( ACTIVE > 0 )); then
    es GET '/_cat/recovery?v&active_only=true&h=index,shard,time,source_node,target_node,bytes_percent,translog_ops_percent' \
      | head -25
  fi

  hr
  UNASSIGNED="$(unassigned_shards)"
  if (( UNASSIGNED > 0 )); then
    printf '%sUnassigned shard nedeni (ilk kayıt):%s\n' "$_C_YLW" "$_C_OFF"
    es GET '/_cluster/allocation/explain' -d '{}' 2>/dev/null \
      | jq -r '"  index=\(.index) shard=\(.shard) primary=\(.primary)\n  \(.allocate_explanation // .can_allocate // "-")"' \
      2>/dev/null || echo "  (explain alınamadı)"
  fi

  sleep "${INTERVAL}"
done
