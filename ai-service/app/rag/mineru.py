"""MinerU 精准解析 API 客户端（v4）。

流程（本地文件）：
1. POST /file-urls/batch 申请上传链接，拿到 batch_id 与 file_urls
2. PUT file_urls[0] 上传 PDF（预签名地址，不带 Authorization）
3. 轮询 GET /extract-results/batch/{batch_id} 直到 state=done
4. 下载 full_zip_url，解压读取 full.md
"""

from __future__ import annotations

import asyncio
import io
import logging
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx

from ..config import Settings, get_settings

log = logging.getLogger(__name__)

STATE_DONE = "done"
STATE_FAILED = "failed"
TERMINAL_STATES = {STATE_DONE, STATE_FAILED}


class MinerUError(RuntimeError):
    """MinerU 解析失败（网络、鉴权、任务失败等）。"""


@dataclass
class MinerUResult:
    markdown: str
    batch_id: str


class MinerUClient:
    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    @property
    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self._settings.mineru_api_token}",
            "Content-Type": "application/json",
        }

    async def parse_pdf(self, pdf_path: Path, filename: str) -> MinerUResult:
        settings = self._settings
        if not settings.mineru_api_token:
            raise MinerUError("缺少 MINERU_API_TOKEN，无法调用 MinerU 解析")

        base = settings.mineru_base_url.rstrip("/")
        timeout = httpx.Timeout(60.0, read=120.0)

        async with httpx.AsyncClient(timeout=timeout) as client:
            batch_id, upload_url = await self._request_upload_url(client, base, filename)
            await self._upload_file(client, upload_url, pdf_path)
            full_zip_url = await self._poll_result(client, base, batch_id)
            markdown = await self._download_markdown(client, full_zip_url)

        if not markdown.strip():
            raise MinerUError("MinerU 解析结果为空")
        return MinerUResult(markdown=markdown, batch_id=batch_id)

    async def _request_upload_url(
        self, client: httpx.AsyncClient, base: str, filename: str
    ) -> tuple[str, str]:
        payload: dict[str, Any] = {
            "enable_formula": True,
            "language": "ch",
            "model_version": self._settings.mineru_model_version,
            "files": [{"name": filename, "is_ocr": True}],
        }
        resp = await client.post(f"{base}/file-urls/batch", headers=self._headers, json=payload)
        data = self._unwrap(resp, "申请上传链接")
        batch_id = data.get("batch_id")
        urls = data.get("file_urls") or []
        if not batch_id or not urls:
            raise MinerUError(f"申请上传链接返回异常: {data}")
        return str(batch_id), str(urls[0])

    async def _upload_file(
        self, client: httpx.AsyncClient, upload_url: str, pdf_path: Path
    ) -> None:
        content = pdf_path.read_bytes()
        resp = await client.put(
            upload_url,
            content=content,
            headers={"Content-Type": "application/pdf"},
        )
        if resp.status_code >= 400:
            raise MinerUError(f"上传 PDF 到 MinerU 失败: HTTP {resp.status_code}")

    async def _poll_result(self, client: httpx.AsyncClient, base: str, batch_id: str) -> str:
        settings = self._settings
        deadline = asyncio.get_event_loop().time() + settings.mineru_timeout_seconds
        url = f"{base}/extract-results/batch/{batch_id}"

        while asyncio.get_event_loop().time() < deadline:
            resp = await client.get(url, headers=self._headers)
            data = self._unwrap(resp, "查询解析结果")
            for item in data.get("extract_result") or []:
                state = str(item.get("state", "")).lower()
                if state == STATE_DONE and item.get("full_zip_url"):
                    return str(item["full_zip_url"])
                if state == STATE_FAILED:
                    raise MinerUError(f"MinerU 解析任务失败: {item.get('err_msg') or '未知原因'}")
            await asyncio.sleep(settings.mineru_poll_interval_seconds)

        raise MinerUError(f"MinerU 解析超时（{settings.mineru_timeout_seconds}s）")

    async def _download_markdown(self, client: httpx.AsyncClient, zip_url: str) -> str:
        resp = await client.get(zip_url)
        if resp.status_code >= 400:
            raise MinerUError(f"下载 MinerU 结果失败: HTTP {resp.status_code}")
        return extract_markdown_from_zip(resp.content)

    @staticmethod
    def _unwrap(resp: httpx.Response, action: str) -> dict[str, Any]:
        if resp.status_code >= 400:
            raise MinerUError(f"MinerU {action}失败: HTTP {resp.status_code} {resp.text[:200]}")
        try:
            body = resp.json()
        except ValueError as exc:
            raise MinerUError(f"MinerU {action}返回非 JSON 内容") from exc
        code = body.get("code", 0)
        if code not in (0, 200):
            raise MinerUError(f"MinerU {action}失败: code={code}, msg={body.get('msg')}")
        return body.get("data") or {}


def extract_markdown_from_zip(content: bytes) -> str:
    """从解析结果压缩包中取出 markdown（优先 full.md）。"""
    with zipfile.ZipFile(io.BytesIO(content)) as zf:
        md_names = [n for n in zf.namelist() if n.lower().endswith(".md")]
        if not md_names:
            raise MinerUError("MinerU 结果包中未找到 markdown 文件")
        preferred = next((n for n in md_names if Path(n).name.lower() == "full.md"), md_names[0])
        return zf.read(preferred).decode("utf-8", errors="ignore")


def parse_with_pypdf(pdf_path: Path) -> str:
    """MinerU 不可用时的本地降级方案（仅提取文本层）。"""
    from pypdf import PdfReader

    reader = PdfReader(str(pdf_path))
    parts: list[str] = []
    for index, page in enumerate(reader.pages):
        try:
            text = page.extract_text() or ""
        except Exception:  # pragma: no cover - 损坏页面兜底
            text = ""
        if text.strip():
            parts.append(f"## 第 {index + 1} 页\n{text.strip()}")
    return "\n\n".join(parts)


def clean_markdown(text: str) -> str:
    """去掉图片/HTML 噪声，保留条款正文，减少无意义 token。"""
    import re

    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()