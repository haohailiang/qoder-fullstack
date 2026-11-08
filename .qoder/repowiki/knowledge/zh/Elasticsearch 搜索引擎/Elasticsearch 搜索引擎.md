---
kind: external_dependency
name: Elasticsearch 搜索引擎
slug: elasticsearch
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Elasticsearch
- 角色：商品搜索索引，由 `ecommerce-product` 的 `ElasticsearchConfig` / `ProductDocument` / `ProductElasticsearchRepository` 维护；提供 `POST /api/product/products/sync-es` 全量同步接口。
- 客户端约束：Docker 编排中通过 `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200` 注入；数据目录挂载至 `/usr/share/elasticsearch/data`。