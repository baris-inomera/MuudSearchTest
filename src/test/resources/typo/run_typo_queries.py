#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Runs each query in test_text/queries.txt against the active-indices search
endpoint and records the top hits in a pipe-delimited results file.

Usage:
  python3 run_typo_queries.py
  python3 run_typo_queries.py --url http://localhost:9090/search/rest/v10/active-indices/search \
      --user search-ui --password <pw> --app-key muud
"""
import argparse
import base64
import json
import urllib.error
import urllib.request

DEFAULT_URL = "http://localhost:9090/search/rest/v10/active-indices/search"
DEFAULT_QUERIES = "../test_text/queries.txt"
DEFAULT_OUT = "actual_results.txt"


def load_queries(path, skip_song=False):
    rows = []
    with open(path, encoding="utf-8") as f:
        next(f)  # header
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            no, query, test_type, logic = line.split("|", 3)
            if skip_song and test_type.startswith("Şarkı"):
                continue
            rows.append((int(no), query))
    return rows


def hit_name_fields(hit):
    """Extract (index, primary_name, secondary_name) from a topHits entry."""
    index = hit.get("index", "")
    data = hit.get("data", {}) or {}
    if index == "muud_performer_flat_v2":
        return index, data.get("performerName", ""), ""
    if index == "muud_album_flat_v2":
        return index, data.get("albumName", ""), data.get("performerNames", "")
    if index == "muud_playlist_flat_v2":
        return index, data.get("playlistName", ""), ""
    if index == "muud_video_flat_v2":
        return index, data.get("songName", ""), data.get("performerName", "")
    if index in ("muud_song_flat_v2", "muud_song_nospace_v2_mini", "muud_song_nospace_v2"):
        return index, data.get("songName", ""), data.get("performerName", "")
    # unknown index type: best-effort
    name = data.get("name") or data.get("title") or ""
    return index, name, ""


def write_row(out, fields):
    cleaned = [str(f).replace("|", "/").replace("\n", " ") if f is not None else "" for f in fields]
    out.write("|".join(cleaned) + "\n")


def search_one(url, text, timeout, headers):
    payload = json.dumps({
        "text": text,
        "suggestion": True,
        "correction": True,
        "limit": 10,
        "offset": 0,
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return resp.status, body, None
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            body = None
        return e.code, body, str(e)
    except Exception as e:
        return -1, None, str(e)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--queries", default=DEFAULT_QUERIES)
    ap.add_argument("--url", default=DEFAULT_URL)
    ap.add_argument("--user", default="search-ui")
    ap.add_argument("--password", default="da5c84b6ab3ea1571e38c292a9ac021a")
    ap.add_argument("--app-key", default="muud")
    ap.add_argument("--timeout", type=float, default=10.0)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--skip-song", action="store_true", help="skip Şarkı (song) type queries")
    args = ap.parse_args()

    headers = {"Content-Type": "application/json"}
    token = base64.b64encode(f"{args.user}:{args.password}".encode()).decode()
    headers["Authorization"] = f"Basic {token}"
    if args.app_key:
        headers["X-SEARCH-APP-KEY"] = args.app_key

    queries = load_queries(args.queries, skip_song=args.skip_song)
    print(f"Loaded {len(queries)} queries from {args.queries}")

    with open(args.out, "w", encoding="utf-8") as out:
        out.write("no|query|http_status|error|corrected|suggested_text|rank|index|name|extra|score\n")
        for no, query in queries:
            status, body, err = search_one(args.url, query, args.timeout, headers)
            if body is None or status != 200:
                write_row(out, [no, query, status, err or "", "", "", "", "", "", "", ""])
                print(f"[{no}] '{query}' -> HTTP {status} error={err}")
                continue

            corrected = body.get("corrected", False)
            suggested = body.get("suggestedText", "") or ""
            hits = body.get("topHits", []) or []
            if not hits:
                write_row(out, [no, query, status, "", corrected, suggested, "", "", "", "", ""])
                print(f"[{no}] '{query}' -> 0 hits (corrected={corrected})")
                continue
            for rank, hit in enumerate(hits, start=1):
                index, name, extra = hit_name_fields(hit)
                score = hit.get("score", "")
                write_row(out, [no, query, status, "", corrected, suggested, rank, index, name, extra, score])
            print(f"[{no}] '{query}' -> {len(hits)} hits (corrected={corrected})")

    print(f"\nRaw actual results written to {args.out}")


if __name__ == "__main__":
    main()
