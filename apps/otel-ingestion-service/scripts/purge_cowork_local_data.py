#!/usr/bin/env python3
"""
Delete Claude Cowork discovery data from local Mongo (+ optional ES traces) for a clean replay.

Removes api_collections (and related api_info, STI, samples) whose hostName
contains 'claude_cowork'. Optionally drops cowork module_info heartbeats and
agent_query_records in Elasticsearch for the account.

Usage:
  python3 purge_cowork_local_data.py --account-id 1000000
  python3 purge_cowork_local_data.py --account-id 1000000 --execute
  python3 purge_cowork_local_data.py --account-id 1000000 --execute --purge-es
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

try:
    from pymongo import MongoClient
except ImportError:
    print("Install pymongo: pip3 install pymongo", file=sys.stderr)
    raise SystemExit(1)

COWORK_HOST_RE = "claude_cowork"
MODULE_VERSION = "cowork-otel"
# STI urls produced by cowork otel ingest (incl. /tool/*, /v1/messages, /cowork/*)
COWORK_STI_URL_RE = r"^(/cowork/|/tool/|/v1/messages|/skills/)"
DEFAULT_ES_INDEX = "agent_query_records"
DEVICE_ID_RE = re.compile(r"^([0-9a-f]{12})\.")


def cowork_orphan_collection_ids(db, registered_col_ids: set[int]) -> list[int]:
    """STI col ids from cowork traffic with no api_collections row (wrong pre-register id)."""
    pipeline = [
        {"$match": {"url": {"$regex": COWORK_STI_URL_RE}}},
        {"$group": {"_id": "$apiCollectionId"}},
    ]
    orphan_ids: list[int] = []
    for row in db.single_type_info.aggregate(pipeline):
        cid = row.get("_id")
        if cid is None or cid in registered_col_ids:
            continue
        if db.api_collections.count_documents({"_id": cid}, limit=1) == 0:
            orphan_ids.append(cid)
    return orphan_ids


def device_ids_from_collections(cols: list[dict]) -> list[str]:
    ids: list[str] = []
    seen: set[str] = set()
    for c in cols:
        host = c.get("hostName") or ""
        m = DEVICE_ID_RE.match(host)
        if not m:
            continue
        did = m.group(1)
        if did not in seen:
            seen.add(did)
            ids.append(did)
    return ids


def es_delete_cowork_traces(
    *,
    es_host: str,
    es_index: str,
    es_api_key: str | None,
    account_id: int,
    device_ids: list[str],
    execute: bool,
) -> int:
    should = [
        {"term": {"serviceId.keyword": "claude_cowork"}},
        {"wildcard": {"serviceId.keyword": "*.claude_cowork"}},
    ]
    if device_ids:
        should.append({"terms": {"deviceId.keyword": device_ids}})

    body = {
        "query": {
            "bool": {
                "filter": [{"term": {"accountId": account_id}}],
                "should": should,
                "minimum_should_match": 1,
            }
        }
    }

    url = f"{es_host.rstrip('/')}/{es_index}/_count"
    count = _es_request(url, body, es_api_key)
    print(f"Elasticsearch {es_index}: {count} agent_query_records for account {account_id} (cowork)")

    if not execute:
        return count

    delete_url = f"{es_host.rstrip('/')}/{es_index}/_delete_by_query?conflicts=proceed"
    result = _es_request(delete_url, body, es_api_key, method="POST")
    deleted = result.get("deleted", 0) if isinstance(result, dict) else 0
    print(f"Deleted ES agent_query_records={deleted}")
    return deleted


def _es_request(url: str, body: dict, api_key: str | None, method: str = "POST"):
    data = json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"ApiKey {api_key}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        print(f"Elasticsearch error ({e.code}): {err}", file=sys.stderr)
        raise SystemExit(1) from e
    except urllib.error.URLError as e:
        print(f"Elasticsearch unreachable: {e}", file=sys.stderr)
        raise SystemExit(1) from e


def main() -> int:
    parser = argparse.ArgumentParser(description="Purge local Cowork Mongo data for an account")
    parser.add_argument("--mongo-uri", default="mongodb://localhost:27017/admin")
    parser.add_argument("--account-id", type=int, default=1000000)
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually delete (default is dry-run preview only)",
    )
    parser.add_argument(
        "--purge-module-info",
        action="store_true",
        help="Also delete module_info rows with currentVersion=cowork-otel",
    )
    parser.add_argument(
        "--purge-es",
        action="store_true",
        help="Also delete cowork agent_query_records from Elasticsearch",
    )
    parser.add_argument("--es-host", default=os.environ.get("ES_HOST", "http://localhost:9200"))
    parser.add_argument(
        "--es-index",
        default=os.environ.get("ES_INDEX_AGENT_QUERY", DEFAULT_ES_INDEX),
    )
    parser.add_argument("--es-api-key", default=os.environ.get("ES_API_KEY"))
    args = parser.parse_args()

    client = MongoClient(args.mongo_uri, serverSelectionTimeoutMS=5000)
    db = client[str(args.account_id)]

    cols = list(
        db.api_collections.find(
            {"hostName": {"$regex": COWORK_HOST_RE}},
            {"_id": 1, "hostName": 1},
        )
    )
    col_ids = [c["_id"] for c in cols]
    registered = set(col_ids)
    orphan_ids = cowork_orphan_collection_ids(db, registered)
    if orphan_ids:
        print(f"Orphan cowork STI collection ids (no api_collections row): {orphan_ids}")
        col_ids = list(dict.fromkeys(col_ids + orphan_ids))
    print(f"Account {args.account_id}: {len(cols)} api_collections matching *{COWORK_HOST_RE}*")
    for c in cols[:20]:
        print(f"  - {c['_id']}: {c.get('hostName')}")
    if len(cols) > 20:
        print(f"  ... and {len(cols) - 20} more")

    device_ids = device_ids_from_collections(cols)

    if not col_ids and not args.purge_es:
        print("Nothing to delete.")
        return 0

    if col_ids:
        print(f"Total collection ids to purge (registered + orphan STI): {len(col_ids)}")
        api_info_q = {"_id.apiCollectionId": {"$in": col_ids}}
        sti_q = {"apiCollectionId": {"$in": col_ids}}
        sample_q = {"_id.apiCollectionId": {"$in": col_ids}}

        api_info_n = db.api_info.count_documents(api_info_q)
        sti_n = db.single_type_info.count_documents(sti_q)
        sample_n = db.sample_data.count_documents(sample_q)
        print(f"Related: api_info={api_info_n}, single_type_info={sti_n}, sample_data={sample_n}")

    mod_n = 0
    mod_q = {"currentVersion": MODULE_VERSION}
    if args.purge_module_info:
        mod_n = db.module_info.count_documents(mod_q)
        print(f"module_info (version={MODULE_VERSION}): {mod_n}")

    if args.purge_es:
        count_body = {
            "query": {
                "bool": {
                    "filter": [{"term": {"accountId": args.account_id}}],
                    "should": [
                        {"term": {"serviceId.keyword": "claude_cowork"}},
                        {"wildcard": {"serviceId.keyword": "*.claude_cowork"}},
                        *([{"terms": {"deviceId.keyword": device_ids}}] if device_ids else []),
                    ],
                    "minimum_should_match": 1,
                }
            }
        }
        if args.es_host:
            url = f"{args.es_host.rstrip('/')}/{args.es_index}/_count"
            try:
                result = _es_request(url, count_body, args.es_api_key)
                es_n = result.get("count", 0) if isinstance(result, dict) else 0
                print(f"Elasticsearch {args.es_index}: {es_n} cowork trace docs")
            except SystemExit:
                if not args.execute:
                    print("(ES count failed — will retry on --execute if needed)")
                else:
                    raise
        else:
            print("ES_HOST not set — skipping ES purge preview")

    if not args.execute:
        print("\nDry run — pass --execute to delete.")
        return 0

    if col_ids:
        api_info_q = {"_id.apiCollectionId": {"$in": col_ids}}
        sti_q = {"apiCollectionId": {"$in": col_ids}}
        sample_q = {"_id.apiCollectionId": {"$in": col_ids}}

        r1 = db.api_info.delete_many(api_info_q)
        r2 = db.single_type_info.delete_many(sti_q)
        r3 = db.sample_data.delete_many(sample_q)
        r4 = db.api_collections.delete_many({"_id": {"$in": col_ids}})
        print(f"Deleted api_info={r1.deleted_count}, sti={r2.deleted_count}, "
              f"sample_data={r3.deleted_count}, api_collections={r4.deleted_count}")

    if args.purge_module_info:
        r5 = db.module_info.delete_many(mod_q)
        print(f"Deleted module_info={r5.deleted_count}")

    if args.purge_es and args.es_host:
        es_delete_cowork_traces(
            es_host=args.es_host,
            es_index=args.es_index,
            es_api_key=args.es_api_key,
            account_id=args.account_id,
            device_ids=device_ids,
            execute=True,
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
