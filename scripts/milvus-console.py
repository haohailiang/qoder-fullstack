"""Milvus 迷你网页控制台：浏览器里看集合、schema、行数与数据（只读）。

用法（仓库根执行，需 ai-service 虚拟环境）：
    ai-service/.venv/Scripts/python.exe scripts/milvus-console.py           # 默认 127.0.0.1:8010
    ai-service/.venv/Scripts/python.exe scripts/milvus-console.py --port 8020 --host 0.0.0.0

依赖：ai-service 已装的 fastapi + uvicorn + httpx；数据经 Milvus REST(19530) 读取。
"""
from __future__ import annotations

import argparse
import os
from typing import Any

import httpx
from fastapi import FastAPI, Query
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

MILVUS = os.getenv("MILVUS_REST", os.getenv("MILVUS_URI", "http://192.168.178.100:19530")).rstrip("/")

app = FastAPI(title="Milvus Console")


async def rest(path: str, payload: dict[str, Any]) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.post(f"{MILVUS}{path}", json=payload)
        resp.raise_for_status()
        return resp.json()


@app.get("/api/collections")
async def collections() -> dict[str, Any]:
    listing = await rest("/v2/vectordb/collections/list", {})
    names = listing.get("data") or []
    out = []
    for name in names:
        try:
            stats = await rest("/v2/vectordb/collections/get_stats", {"collectionName": name})
            rows = stats.get("data", {}).get("rowCount", 0)
        except Exception:
            rows = None
        out.append({"name": name, "rowCount": rows})
    return {"collections": out}


@app.get("/api/collection")
async def collection(name: str = Query(...)) -> dict[str, Any]:
    desc = await rest("/v2/vectordb/collections/describe", {"collectionName": name})
    stats = await rest("/v2/vectordb/collections/get_stats", {"collectionName": name})
    data = desc.get("data", {})
    fields = []
    for f in data.get("fields", []):
        params = {p.get("key"): p.get("value") for p in (f.get("params") or [])}
        fields.append(
            {
                "name": f.get("name"),
                "type": f.get("type"),
                "primaryKey": f.get("primaryKey", False),
                "dim": params.get("dim"),
                "maxLength": params.get("max_length"),
            }
        )
    return {
        "name": name,
        "fields": fields,
        "indexes": data.get("indexes") or [],
        "load": data.get("load"),
        "rowCount": stats.get("data", {}).get("rowCount", 0),
    }


class QueryReq(BaseModel):
    collection: str
    filter: str = ""
    limit: int = 20
    fields: list[str] = []


@app.post("/api/query")
async def query_rows(req: QueryReq) -> dict[str, Any]:
    desc = await rest("/v2/vectordb/collections/describe", {"collectionName": req.collection})
    all_fields = [f.get("name") for f in desc.get("data", {}).get("fields", [])]
    picked = [f for f in (req.fields or all_fields) if f in all_fields]
    try:
        res = await rest(
            "/v2/vectordb/entities/query",
            {
                "collectionName": req.collection,
                "filter": req.filter or "",
                "outputFields": picked,
                "limit": max(1, min(req.limit, 500)),
            },
        )
    except httpx.HTTPStatusError as exc:
        return {"error": f"REST {exc.response.status_code}: {exc.response.text[:300]}", "rows": []}
    return {"rows": res.get("data") or [], "fields": picked}


PAGE = """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>Milvus 控制台</title>
<style>
 :root { color-scheme: dark; }
 body { margin:0; font:14px/1.5 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif; background:#0f1117; color:#e6e8ee; }
 header { padding:14px 20px; border-bottom:1px solid #222634; display:flex; gap:16px; align-items:baseline; }
 header h1 { font-size:16px; margin:0; font-weight:600; }
 header span { color:#8b93a7; font-size:12px; }
 main { padding:16px 20px; display:grid; grid-template-columns: 260px 1fr; gap:16px; }
 .panel { background:#151824; border:1px solid #222634; border-radius:8px; padding:12px; }
 .panel h2 { font-size:13px; margin:0 0 10px; color:#8b93a7; font-weight:600; letter-spacing:.04em; }
 .coll { padding:7px 9px; border-radius:6px; cursor:pointer; display:flex; justify-content:space-between; }
 .coll:hover { background:#1d2130; }
 .coll.active { background:#233046; }
 .coll b { font-weight:600; }
 .coll i { color:#8b93a7; font-style:normal; font-size:12px; }
 table { border-collapse:collapse; width:100%; font-size:13px; }
 th, td { text-align:left; padding:6px 8px; border-bottom:1px solid #222634; vertical-align:top; }
 th { color:#8b93a7; font-weight:600; font-size:12px; }
 td { color:#cfd4e3; word-break:break-word; }
 input, button { background:#1b2030; color:#e6e8ee; border:1px solid #2b3448; border-radius:6px; padding:7px 10px; font:inherit; }
 button { cursor:pointer; }
 button:hover { background:#243049; }
 .row { display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap; }
 .muted { color:#8b93a7; }
 .chip { display:inline-block; padding:2px 7px; border-radius:999px; background:#232a3d; color:#b9c2d8; font-size:12px; margin:2px 4px 2px 0; }
 pre { white-space:pre-wrap; margin:0; }
</style></head><body>
<header><h1>Milvus 控制台</h1><span id="ep"></span></header>
<main>
  <div class="panel"><h2>集合</h2><div id="colls" class="muted">加载中…</div></div>
  <div class="panel">
    <h2>详情</h2>
    <div id="detail" class="muted">← 选择左侧集合</div>
    <div id="queryBox" style="display:none">
      <div class="row" style="margin-top:14px">
        <input id="filter" placeholder='过滤表达式，如 product_id == 1（留空=全部）' style="flex:1;min-width:260px">
        <input id="limit" type="number" value="20" min="1" max="500" style="width:90px">
        <button onclick="loadRows()">查询数据</button>
      </div>
      <div id="rows" class="muted"></div>
    </div>
  </div>
</main>
<script>
let current = null;
const MILVUS_EP = "MILVUS_EP_PLACEHOLDER";
const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

async function loadColls(){
  const r = await fetch('/api/collections').then(r=>r.json());
  const box = $('colls');
  if(!r.collections.length){ box.textContent='（还没有集合）'; return; }
  box.innerHTML = r.collections.map(c =>
    `<div class="coll" data-n="${esc(c.name)}"><b>${esc(c.name)}</b><i>${c.rowCount ?? '?'} 行</i></div>`).join('');
  box.querySelectorAll('.coll').forEach(el => el.onclick = () => openColl(el.dataset.n));
}

async function openColl(name){
  current = name;
  document.querySelectorAll('.coll').forEach(el => el.classList.toggle('active', el.dataset.n === name));
  const d = await fetch('/api/collection?name=' + encodeURIComponent(name)).then(r=>r.json());
  $('detail').innerHTML = `
    <div><b>${esc(d.name)}</b> <span class="muted">· ${d.rowCount} 行 · ${esc(d.load||'')}</span></div>
    <div style="margin-top:10px">${d.fields.map(f =>
      `<span class="chip">${esc(f.name)}: ${esc(f.type)}${f.dim?' dim='+f.dim:''}${f.maxLength?' len='+f.maxLength:''}${f.primaryKey?' PK':''}</span>`).join('')}</div>
    <div style="margin-top:8px" class="muted">索引：${(d.indexes||[]).map(i=>esc(i.fieldName)+'/'+esc(i.metricType||'')).join('，')||'无'}</div>`;
  $('queryBox').style.display = 'block';
  loadRows();
}

async function loadRows(){
  if(!current) return;
  const body = {collection: current, filter: $('filter').value, limit: Number($('limit').value) || 20,
                fields: ['chunk_id','product_id','doc_id','chunk_index','source','text']};
  const r = await fetch('/api/query', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body)})
             .then(r=>r.json());
  if(r.error){ $('rows').innerHTML = '<pre style="color:#ff9a9a">'+esc(r.error)+'</pre>'; return; }
  if(!r.rows.length){ $('rows').innerHTML = '<span class="muted">没有数据</span>'; return; }
  const cols = r.fields.filter(f => f !== 'vector');
  const present = cols.filter(c => r.rows.some(row => row[c] !== undefined && row[c] !== null));
  $('rows').innerHTML = '<table><thead><tr>' + present.map(c=>`<th>${esc(c)}</th>`).join('') + '</tr></thead><tbody>' +
    r.rows.map(row => '<tr>' + present.map(c => {
      let v = row[c]; if (typeof v === 'object') v = JSON.stringify(v);
      if (typeof v === 'string' && v.length > 220) v = v.slice(0,220) + '…';
      return `<td>${esc(v ?? '')}</td>`;
    }).join('') + '</tr>').join('') + '</tbody></table>';
}

$('ep').textContent = 'Milvus REST: ' + MILVUS_EP;
loadColls();
</script>
</body></html>
"""


@app.get("/", response_class=HTMLResponse)
async def index() -> str:
    return PAGE.replace("MILVUS_EP_PLACEHOLDER", MILVUS)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8010)
    args = ap.parse_args()
    import uvicorn

    print(f"Milvus console -> http://{args.host}:{args.port}   (target {MILVUS})")
    uvicorn.run(app, host=args.host, port=args.port, log_level="warning")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
