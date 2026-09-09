#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FAZ 3 — ddp-prod-ist-master-3 (10.95.27.28) decommission
#
# Hedef topoloji 2 IST + 1 ANK master. Şu an 3 IST + 1 ANK = 4 master var
# (çift sayı, quorum 3). .28 çıkınca 3 master / quorum 2 olacak.
#
# NEDEN EN SONA BIRAKILDI: bu adım master sayısını düşürür. Önce yapılsaydı
# migration'ın en riskli bölümünü sıfır tolerans ile geçirmiş olurduk.
#
# NEDEN "servisi durdur" YETMEZ: master-eligible bir node'u haber vermeden
# kapatırsanız voting configuration onu hâlâ üye sayar ve quorum hesabı bozulur.
# Önce voting_config_exclusions ile oylama listesinden düşürüyoruz.
#
# .28 sadece [master] rolünde — üzerinde shard YOK, veri taşınması gerekmiyor.
#
#   ./06-decommission-ist-master-3.sh
# ---------------------------------------------------------------------------
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

preamble
section "FAZ 3 — ${IST_MASTER_3_NAME} DECOMMISSION"

[[ "$(cluster_status)" == "green" ]] || die "Cluster green değil. Bu adıma green olmadan girilmez."

# --- 1. Ön koşullar --------------------------------------------------------
section "1. Ön koşullar"
MASTERS="$(master_eligible_nodes)"
printf 'Master-eligible node\x27lar:\n'; printf '  %s\n' ${MASTERS}

grep -qx "${ANK_MASTER_1_NAME}" <<<"${MASTERS}" \
  || die "${ANK_MASTER_1_NAME} master listesinde YOK. Ankara master'ı katılmadan .28 çıkarılamaz —
     aksi halde 3 IST master'dan 2'ye düşer ve Ankara'da hiç master olmaz."
grep -qx "${IST_MASTER_3_NAME}" <<<"${MASTERS}" \
  || { ok "${IST_MASTER_3_NAME} zaten cluster'da değil — bu adım tamamlanmış."; exit 0; }
ok "${ANK_MASTER_1_NAME} cluster'da; .28 güvenle çıkarılabilir."

ELECTED="$(es GET '/_cat/master?format=json&h=node' | jq -r '.[0].node')"
log "Şu anki elected master: ${ELECTED}"
[[ "${ELECTED}" != "${IST_MASTER_3_NAME}" ]] \
  || warn "Elected master çıkarılacak node. Exclusion sonrası yeni seçim olacak — normal, birkaç saniye sürer."

# .28'in shard taşımadığını doğrula
SHARDS_ON_28="$(es GET '/_cat/allocation?format=json&h=node,shards' \
  | jq -r --arg n "${IST_MASTER_3_NAME}" '[.[] | select(.node == $n) | .shards] | .[0] // "0"')"
log "${IST_MASTER_3_NAME} üzerindeki shard sayısı: ${SHARDS_ON_28}"
[[ "${SHARDS_ON_28}" == "0" ]] \
  || die "Beklenmedik: node üzerinde ${SHARDS_ON_28} shard var. Önce boşaltın:
     PUT /_cluster/settings {\"persistent\":{\"cluster.routing.allocation.exclude._name\":\"${IST_MASTER_3_NAME}\"}}"

snapshot_cluster_settings "before-decommission" >/dev/null

NODE_ID_28="$(es GET '/_cat/nodes?format=json&h=id,name' \
  | jq -r --arg n "${IST_MASTER_3_NAME}" '.[] | select(.name == $n) | .id')"
log "Node id: ${NODE_ID_28}"

# --- 2. Voting config exclusion -------------------------------------------
section "2. Voting configuration exclusion"
cat <<EOF
  POST /_cluster/voting_config_exclusions?node_names=${IST_MASTER_3_NAME}

  Bu, node'u master oylamasından düşürür. Quorum 3'ten 2'ye iner. Node hâlâ
  ayakta ve cluster üyesi kalır — sadece oy hakkı kalmaz.
EOF
confirm "Exclusion uygulansın mı?"

es POST "/_cluster/voting_config_exclusions?node_names=${IST_MASTER_3_NAME}&timeout=120s" >/dev/null \
  || die "Exclusion başarısız. ES 7.8 öncesi sürümlerde endpoint farklı:
     POST /_cluster/voting_config_exclusions/${IST_MASTER_3_NAME}"
ok "Exclusion uygulandı."

log "Voting configuration'dan düşmesi bekleniyor…"
for i in $(seq 1 30); do
  LCC="$(es GET '/_cluster/state/metadata?filter_path=metadata.cluster_coordination' \
        | jq -r '.metadata.cluster_coordination.last_committed_config // [] | join(" ")')"
  if ! grep -qw "${NODE_ID_28}" <<<"${LCC}"; then
    ok "Voting configuration'dan düştü. Kalan üye sayısı: $(wc -w <<<"${LCC}" | tr -d ' ')"
    break
  fi
  printf '%s  … (%s/30)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
  if (( i == 30 )); then die "Node 150s içinde voting config'ten düşmedi. ROLLBACK.md -> R4"; fi
done

es GET '/_cluster/state/metadata?filter_path=metadata.cluster_coordination' | jq '.'

# --- 3. Servisi durdur -----------------------------------------------------
section "3. Servisi durdur"
cat <<EOF
  ŞİMDİ ${IST_MASTER_3_NAME} (${IST_MASTER_3_IP}) üzerinde:

      systemctl stop elasticsearch

  !!! path.data'yı (/data/elasticsearch-master) SİLMEYİN.
  !!! Rollback gerekirse (ROLLBACK.md -> R4) node'u olduğu gibi geri açacağız.
  !!! Silme işlemi ancak 07-post-checks.sh geçtikten ve birkaç gün stabil
  !!! çalıştıktan sonra yapılır.
EOF
confirm "${IST_MASTER_3_NAME} durduruldu mu?"

log "Node'un cluster'dan düşmesi bekleniyor…"
for i in $(seq 1 30); do
  if ! es GET '/_cat/nodes?format=json&h=name' \
       | jq -e --arg n "${IST_MASTER_3_NAME}" 'map(.name) | index($n) != null' >/dev/null 2>&1; then
    ok "Node cluster listesinden düştü."; break
  fi
  printf '%s  … (%s/30)%s\n' "$_C_DIM" "${i}" "$_C_OFF"; sleep 5
  if (( i == 30 )); then warn "Node hâlâ listede görünüyor — servis gerçekten durdu mu?"; fi
done

wait_for_green 900 || die "Cluster green olmadı. ROLLBACK.md -> R4"

# --- 4. Exclusion listesini temizle ---------------------------------------
section "4. Exclusion listesi temizleniyor"
cat <<'EOF'
  Exclusion listesi kalıcı bırakılmaz: dolu kalırsa ileride aynı isimde bir node
  eklemek istediğinizde sessizce oylama dışında kalır.
EOF
es DELETE '/_cluster/voting_config_exclusions' >/dev/null
ok "Exclusion listesi temizlendi."

# --- 5. Doğrulama ----------------------------------------------------------
section "5. Doğrulama"
es GET '/_cat/nodes?v&h=name,ip,node.role,master,version&s=name'
echo

MASTER_N="$(master_eligible_nodes | wc -l | tr -d ' ')"
N="$(node_count)"
log "Master-eligible: ${MASTER_N} (hedef ${TARGET_MASTER_COUNT})   Toplam node: ${N} (hedef ${TARGET_NODE_COUNT})"
(( MASTER_N == TARGET_MASTER_COUNT )) || warn "Master sayısı hedeften farklı!"
(( N == TARGET_NODE_COUNT ))          || warn "Node sayısı hedeften farklı!"

ok "FAZ 3 tamam. Sıradaki adım: ./07-post-checks.sh"
