#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Grades test_code/actual_results.txt against test_text/expected_results.txt
and writes a pass/fail report.

Matching rules (derived from the "Beklenen Sonuç" column of
yazim_toleransi_v4.xlsx):
  - artist:              a performer hit within the expected rank whose
                          name matches the expected artist name.
  - artist_or_playlist:  a performer hit matching the artist name, OR a
                          playlist hit whose name contains the artist name,
                          within the expected rank.
  - song:                a hit exposing both a song title and its performer
                          (only muud_video_flat_v2 hits carry this pairing
                          in the active indices) matching both the expected
                          performer and song title, within the expected rank.

Usage:
  python3 check_typo_results.py
  python3 check_typo_results.py --expected ../test_text/expected_results.txt \
      --actual actual_results.txt --out test_report.txt
"""
import argparse
from collections import defaultdict

DEFAULT_EXPECTED = "../test_text/expected_results.txt"
DEFAULT_ACTUAL = "actual_results.txt"
DEFAULT_OUT = "test_report.txt"

TR_MAP = str.maketrans({
    "İ": "i", "I": "i", "ı": "i",
    "Ş": "s", "ş": "s",
    "Ğ": "g", "ğ": "g",
    "Ü": "u", "ü": "u",
    "Ö": "o", "ö": "o",
    "Ç": "c", "ç": "c",
})


def norm(s):
    return (s or "").strip().translate(TR_MAP).lower()


def load_expected(path):
    expected = {}
    with open(path, encoding="utf-8") as f:
        next(f)
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            no, kind, rank, field1, field2 = line.split("|", 4)
            expected[int(no)] = {"kind": kind, "rank": int(rank), "field1": field1, "field2": field2}
    return expected


def load_actual(path):
    by_no = defaultdict(lambda: {"query": "", "status": None, "error": "", "hits": []})
    with open(path, encoding="utf-8") as f:
        next(f)
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("|")
            no, query, status, error, corrected, suggested, rank, index, name, extra, score = (parts + [""] * 11)[:11]
            entry = by_no[int(no)]
            entry["query"] = query
            entry["status"] = status
            entry["error"] = error
            entry["corrected"] = corrected
            entry["suggested"] = suggested
            if rank:
                entry["hits"].append({
                    "rank": int(rank), "index": index, "name": name, "extra": extra, "score": score,
                })
    return by_no


def evaluate(no, exp, actual):
    """Returns (passed: bool, detail: str)."""
    if actual["status"] != "200":
        return False, f"HTTP {actual['status']} error={actual['error']}"

    hits = actual["hits"]
    if not hits:
        return False, "0 hits returned"

    rank_limit = exp["rank"]
    kind = exp["kind"]

    def matches(hit):
        if kind == "artist":
            return hit["index"] == "muud_performer_flat_v2" and norm(hit["name"]) == norm(exp["field1"])
        if kind == "artist_or_playlist":
            if hit["index"] == "muud_performer_flat_v2" and norm(hit["name"]) == norm(exp["field1"]):
                return True
            if hit["index"] == "muud_playlist_flat_v2" and norm(exp["field1"]) in norm(hit["name"]):
                return True
            return False
        if kind == "song":
            return (hit["index"] in ("muud_video_flat_v2", "muud_song_flat_v2", "muud_song_nospace_v2_mini", "muud_song_nospace_v2")
                    and norm(hit["name"]) == norm(exp["field2"])
                    and norm(hit["extra"]) == norm(exp["field1"]))
        return False

    within_rank = [h for h in hits if h["rank"] <= rank_limit]
    if any(matches(h) for h in within_rank):
        return True, f"matched within top-{rank_limit}"

    # not within the required rank -- check if it exists further down, for a richer failure message
    later = [h for h in hits if h["rank"] > rank_limit and matches(h)]
    if later:
        return False, f"expected within top-{rank_limit}, found at rank {later[0]['rank']}"

    if kind == "song":
        return False, f"no matching song hit for '{exp['field1']} – {exp['field2']}' in top-{len(hits)}"
    return False, f"expected '{exp['field1']}' not found in top-{len(hits)} hits"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--expected", default=DEFAULT_EXPECTED)
    ap.add_argument("--actual", default=DEFAULT_ACTUAL)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--skip-song", action="store_true", help="skip song-kind expectations")
    args = ap.parse_args()

    expected = load_expected(args.expected)
    actual = load_actual(args.actual)

    results = []
    for no in sorted(expected):
        exp = expected[no]
        if args.skip_song and exp["kind"] == "song":
            continue
        act = actual.get(no, {"query": "", "status": None, "error": "not run", "hits": []})
        passed, detail = evaluate(no, exp, act)
        results.append((no, act["query"], passed, detail))

    with open(args.out, "w", encoding="utf-8") as out:
        out.write("no|query|result|detail\n")
        for no, query, passed, detail in results:
            out.write(f"{no}|{query}|{'PASS' if passed else 'FAIL'}|{detail}\n")

    passed_n = sum(1 for r in results if r[2])
    failed = [r for r in results if not r[2]]
    print(f"Total: {len(results)}  Passed: {passed_n}  Failed: {len(failed)}")
    print(f"Report written to {args.out}\n")

    if failed:
        print("Failed queries:")
        for no, query, _, detail in failed:
            print(f"  [{no}] '{query}' -> {detail}")


if __name__ == "__main__":
    main()
