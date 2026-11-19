"""查看 Milvus 库里的内容：集合列表、schema、行数、样本数据。

用法（仓库根执行，需 ai-service 的虚拟环境）：
    ai-service/.venv/Scripts/python.exe scripts/inspect-milvus.py            # 概览所有集合
    ai-service/.venv/Scripts/python.exe scripts/inspect-milvus.py -c insurance_chunks -n 5   # 看某集合 5 条
"""
from __future__ import annotations

import argparse
import json
import os
from typing import Any

from pymilvus import MilvusClient

URI = os.getenv("MILVUS_URI", "http://192.168.178.100:19530")
TOKEN = os.getenv("MILVUS_TOKEN") or None


def field_list(desc: dict[str, Any]) -> list[dict[str, Any]]:
    fields = desc.get("fields") or []
    out = []
    for f in fields:
        item = {
            "name": f.get("name"),
            "type": str(f.get("type")),
            "is_primary": bool(f.get("is_primary")),
            "params": f.get("params") or {},
        }
        out.append(item)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("-c", "--collection", help="集合名，指定则只看该集合")
    ap.add_argument("-n", "--rows", type=int, default=5, help="每个集合展示的样本行数，0 表示不展示")
    args = ap.parse_args()

    client = MilvusClient(uri=URI, token=TOKEN)
    print(f"uri={URI}  server={client.get_server_version()}")

    names = [args.collection] if args.collection else client.list_collections()
    if not names:
        print("(没有任何集合)")
        return 0

    for name in names:
        if not client.has_collection(name):
            print(f"\n[{name}] 不存在")
            continue
        desc = client.describe_collection(name)
        print(f"\n===== {name} =====")
        print("字段：")
        for f in field_list(desc):
            extra = f" dim={f['params'].get('dim')}" if f["params"].get("dim") else ""
            pk = " PK" if f["is_primary"] else ""
            print(f"  - {f['name']}: {f['type']}{extra}{pk}{'' if not f['params'].get('max_length') else ' max_length=' + str(f['params']['max_length'])}")

        # 行数：2.4 用 query count(*)，需要先 load
        try:
            client.load_collection(name)
        except Exception as exc:  # 已 load 或无需 load
            print(f"  (load 跳过: {exc})")

        try:
            rows = client.query(collection_name=name, filter="", output_fields=["count(*)"])
            total = rows[0]["count(*)"] if rows else 0
            print(f"行数: {total}")
        except Exception as exc:
            print(f"行数查询失败: {exc}")
            total = None

        if args.rows > 0 and total:
            sample_fields = [f["name"] for f in field_list(desc) if f["name"] != "vector"]
            try:
                data = client.query(
                    collection_name=name,
                    filter="",
                    output_fields=sample_fields,
                    limit=args.rows,
                )
                print(f"样本（向量列已省略，共 {args.rows} 条上限）：")
                for row in data:
                    print("  " + json.dumps(row, ensure_ascii=False, default=str))
            except Exception as exc:
                print(f"样本查询失败: {exc}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
