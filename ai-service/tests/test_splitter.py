"""切分参数与中文分隔符测试。"""

from __future__ import annotations

from app.config import get_settings
from app.rag.ingest import CHINESE_SEPARATORS, build_splitter, split_markdown


def test_build_splitter_uses_configured_size():
    settings = get_settings()
    splitter = build_splitter(settings)

    assert splitter._chunk_size == settings.chunk_size
    assert splitter._chunk_overlap == settings.chunk_overlap


def test_chinese_separators_registered():
    splitter = build_splitter()

    # 中文合同常见的句号/分号必须参与切分，否则长条款会被硬切
    assert "。" in splitter._separators
    assert "；" in splitter._separators
    assert splitter._separators == CHINESE_SEPARATORS


def test_split_markdown_keeps_sentence_boundaries():
    # 每句约 60 字，共 40 句 → 必然被切成多块，且不应在句中截断
    sentence = "本保险合同的等待期为三十日，自合同生效之日起计算，期间发生的疾病不予赔付。"
    markdown = "".join(f"{index + 1}、{sentence}\n" for index in range(40))

    chunks = split_markdown(markdown)
    settings = get_settings()

    assert len(chunks) > 1
    assert all(chunk.strip() for chunk in chunks)
    for chunk in chunks:
        assert len(chunk) <= settings.chunk_size + 20
        assert chunk.endswith("。")


def test_split_markdown_returns_empty_for_blank_text():
    assert split_markdown("   \n\n  ") == []