#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 2 — Allocation awareness'ı devreye al
#
# Bu adım migration'ın kalbi. Buradan sonra ES her shard'ın bir kopyasını
# İstanbul'da, bir kopyasını Ankara'da tutmaya zorlanır ve ~1 tam veri kopyası
# WAN üzerinden Ankara'ya taşınır.
#
# Ayarlar yaml'a DEĞİL, persistent cluster setting olarak veriliyor:
# dynamic ayarlar oldukları için rollback tek API çağrısı (./rollback.sh R2).
#
#   ./04-enable-awareness.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
section "FAZ 2 — ALLOCATION AWARENESS"

[[ "$(cluster_status)" == "green" ]] || die "Cluster green değil. Awareness'ı green olmadan açmayın."

# --- 1. Ön koşul: her iki zone da mevcut mu? ------------------------------
section "1. Zone doğrulaması"
RACKS="$(node_rack_map)"
IST_N="$(jq -r --arg z "${ZONE_IST}" '[to_entries[] | select(.value == $z)] | length' <<<"${RACKS}")"
ANK_N="$(jq -r --arg z "${ZONE_ANK}" '[to_entries[] | select(.value == $z)] | length' <<<"${RACKS}")"
log "${ZONE_IST}: ${IST_N} node   ${ZONE_ANK}: ${ANK_N} node"
(( ANK_N >= 2 )) || die "Ankara'da yeterli node yok (${ANK_N}). force awareness açılırsa replica'lar
     unassigned kalır ve cluster yellow olur. Önce ./03-join-ankara.sh tamamlansın."
ok "Her iki zone da mevcut."

# --- 2. Ön koşul: replica sayıları ----------------------------------------
section "2. Replica denetimi (SON KONTROL)"
IDX="$(es GET '/_cat/indices?format=json&h=index,rep&expand_wildcards=open')"
MULTI="$(jq -r '[.[] | select((.rep|tonumber) > 1) | "\(.index)(rep=\(.rep))"] | join(" ")' <<<"${IDX}")"
ZERO="$(jq -r '[.[] | select((.rep|tonumber) == 0) | .index] | join(" ")' <<<"${IDX}")"

if [[ -n "${MULTI}" ]]; then
  fail "rep>1 olan index'ler var: ${MULTI}"
  cat <<'EOF'
  2 zone'lu forced awareness shard başına 2 kopya dağıtabilir. Fazla replica'lar
  KALICI OLARAK unassigned kalır ve cluster sürekli yellow görünür.
  Düzeltme:  PUT /<index>/_settings {"index":{"number_of_replicas":1}}
EOF
  confirm "Bunu bilerek kabul edip devam etmek istiyor musunuz?"
fi
[[ -z "${ZERO}" ]] || warn "rep=0 (Ankara'ya kopyalanmayacak) index'ler: ${ZERO}"
[[ -n "${MULTI}${ZERO}" ]] || ok "Tüm index'lerde rep=1 — 2 zone için doğru değer."

snapshot_cluster_settings "before-awareness" >/dev/null

# --- 3. Awareness'ı aç ----------------------------------------------------
section "3. Awareness ayarları"
cat <<EOF
  cluster.routing.allocation.awareness.attributes            = ${AWARENESS_ATTR}
  cluster.routing.allocation.awareness.force.${AWARENESS_ATTR}.values = ${ZONE_IST},${ZONE_ANK}

  'force' ne yapar: aynı shard'ın primary ve replica'sının AYNI zone'da durmasını
  YASAKLAR. Böylece bir lokasyon komple düşse bile her shard'ın diğer lokasyonda
  bir kopyası bulunur.

  BUNDAN SONRA ~1 tam veri kopyası ${RECOVERY_MAX_BYTES_PER_SEC} hızıyla Ankara'ya
  taşınmaya başlayacak. Cluster bu sırada YELLOW görünebilir — bu normaldir,
  taşınan replica'lar geçici olarak unassigned sayılır.
EOF
confirm "Awareness devreye alınsın mı?"

es PUT /_cluster/settings -d "$(jq -nc \
    --arg attr "${AWARENESS_ATTR}" \
    --arg key  "cluster.routing.allocation.awareness.force.${AWARENESS_ATTR}.values" \
    --arg vals "${ZONE_IST},${ZONE_ANK}" \
    '{persistent: ({"cluster.routing.allocation.awareness.attributes": $attr} + {($key): $vals})}')" \
  | jq '.persistent'
ok "Awareness aktif."

# --- 4. Taşımayı izle -----------------------------------------------------
section "4. Relocation"
log "İzleme için ayrı bir terminalde: ./05-watch-relocation.sh"
echo
if ! wait_for_green 21600; then   # 6 saat
  cat <<EOF

  Green'e ulaşılamadı. Teşhis:
    curl -u \$ES_USER:\$ES_PASSWORD '${ES_URL}/_cluster/allocation/explain?pretty'

  Sık görülen nedenler:
    * rep>1 olan index -> fazla kopya yerleştirilemiyor (yukarıdaki uyarı)
    * Ankara disk doluluğu high watermark'ı aştı (%85)
        curl '${ES_URL}/_cat/allocation?v'
    * WAN hattı taşımayı bitiremiyor -> hâlâ relocating>0 ise sadece bekleyin
    * Sertifika/ağ sorunu -> node düştü

  Geri almak için: ./rollback.sh awareness      (ROLLBACK.md -> R2)
EOF
  die "Faz 2 tamamlanamadı."
fi

# --- 5. Rebalance'ı geri aç -----------------------------------------------
section "5. Rebalance geri açılıyor"
es PUT /_cluster/settings -d '{"persistent":{"cluster.routing.rebalance.enable":null}}' >/dev/null
ok "cluster.routing.rebalance.enable=null (default: all)"
wait_for_green 3600

ok "FAZ 2 tamam. Sıradaki adım: ./06-decommission-ist-master-3.sh"
