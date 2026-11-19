"""Milvus 部署冒烟测试：按 ai-service 的集合结构建表、写入、检索、清理。

用法（在仓库根执行）：
    ai-service/.venv/Scripts/python.exe scripts/smoke-milvus.py
"""
from __future__ import annotations

import os
import random
import sys

from pymilvus import DataType, MilvusClient

URI = os.getenv("MILVUS_URI", "http://192.168.178.100:19530")
COLLECTION = os.getenv("MILVUS_COLLECTION", "insurance_chunks")
DIM = int(os.getenv("EMBEDDING_DIM", "1024"))

SMOKE_DOC_ID = "smoke-test-doc"


def main() -> int:
    client = MilvusClient(uri=URI)
    print(f"connected: {URI}, pymilvus {__import__('pymilvus').__version__}")
    print("server version:", client.get_server_version())

    if client.has_collection(COLLECTION):
        client.load_collection(COLLECTION)
        print(f"collection exists: {COLLECTION}")
    else:
        schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("chunk_id", DataType.VARCHAR, is_primary=True, max_length=96)
        schema.add_field("product_id", DataType.INT64)
        schema.add_field("doc_id", DataType.VARCHAR, max_length=32)
        schema.add_field("chunk_index", DataType.INT64)
        schema.add_field("source", DataType.VARCHAR, max_length=255)
        schema.add_field("text", DataType.VARCHAR, max_length=8192)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=DIM)

        index_params = client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
        index_params.add_index(field_name="product_id", index_type="AUTOINDEX")

        client.create_collection(
            collection_name=COLLECTION, schema=schema, index_params=index_params
        )
        client.load_collection(COLLECTION)
        print(f"collection created: {COLLECTION} (dim={DIM})")

    rnd = random.Random(42)
    vectors = [[rnd.uniform(-1, 1) for _ in range(DIM)] for _ in range(3)]
    rows = [
        {
            "chunk_id": f"{SMOKE_DOC_ID}-{i}",
            "product_id": 1,
            "doc_id": SMOKE_DOC_ID,
            "chunk_index": i,
            "source": "smoke-test.pdf",
            "text": f"冒烟测试片段 {i}",
            "vector": vectors[i],
        }
        for i in range(3)
    ]

    client.delete(collection_name=COLLECTION, filter=f'doc_id == "{SMOKE_DOC_ID}"')
    client.upsert(collection_name=COLLECTION, data=rows)
    client.flush(COLLECTION)
    print(f"upserted {len(rows)} rows")

    hits = client.search(
        collection_name=COLLECTION,
        data=[vectors[0]],
        anns_field="vector",
        filter="product_id == 1",
        limit=3,
        output_fields=["text", "doc_id", "chunk_index"],
        search_params={"metric_type": "COSINE", "params": {}},
    )
    top = hits[0][0]
    print(f"search top1: score={top['distance']:.4f} text={top['entity']['text']}")

    count = client.query(
        collection_name=COLLECTION, filter='doc_id == "smoke-test-doc"', output_fields=["count(*)"]
    )
    print("count(smoke rows):", count[0]["count(*)"])

    client.delete(collection_name=COLLECTION, filter=f'doc_id == "{SMOKE_DOC_ID}"')
    client.flush(COLLECTION)
    print("cleanup done -> OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
