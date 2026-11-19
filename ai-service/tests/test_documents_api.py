"""文档接口测试：权限、文件校验（类型/魔数/大小）、列表、重建与删除。"""

from __future__ import annotations

from pathlib import Path

from app.api import documents as documents_api
from app.config import get_settings
from app.models import InsuranceDocument

ADMIN_HEADERS = {"X-User-Id": "1", "X-User-Name": "admin", "X-User-Role": "ADMIN"}
USER_HEADERS = {"X-User-Id": "2", "X-User-Name": "alice", "X-User-Role": "USER"}

PDF_BYTES = b"%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%EOF"


def _upload(client, headers, filename="policy.pdf", content=PDF_BYTES, product_id="9"):
    return client.post(
        "/documents/upload",
        data={"productId": product_id},
        files={"file": (filename, content, "application/pdf")},
        headers=headers,
    )


def test_upload_requires_admin(client):
    response = _upload(client, USER_HEADERS)

    assert response.status_code == 403


def test_upload_rejects_missing_identity(client):
    response = _upload(client, {})

    assert response.status_code == 401


def test_upload_rejects_non_pdf_extension(client):
    response = _upload(client, ADMIN_HEADERS, filename="policy.txt")

    assert response.status_code == 400
    assert "PDF" in response.json()["detail"]


def test_upload_rejects_invalid_magic(client):
    response = _upload(client, ADMIN_HEADERS, content=b"not a real pdf")

    assert response.status_code == 400
    assert "合法 PDF" in response.json()["detail"]


def test_upload_rejects_oversized_file(client, monkeypatch, tmp_path):
    settings = get_settings().model_copy(
        update={"max_pdf_mb": 1, "data_dir": str(tmp_path / "data")}
    )
    monkeypatch.setattr(documents_api, "get_settings", lambda: settings)

    payload = PDF_BYTES + b"0" * (1024 * 1024 + 1)
    response = _upload(client, ADMIN_HEADERS, content=payload)

    assert response.status_code == 413


def test_upload_success_schedules_ingest(client, monkeypatch, tmp_path, db):
    settings = get_settings().model_copy(update={"data_dir": str(tmp_path / "data")})
    monkeypatch.setattr(documents_api, "get_settings", lambda: settings)
    ingested: list[str] = []
    monkeypatch.setattr(documents_api, "run_ingest", lambda doc_id: ingested.append(doc_id))

    response = _upload(client, ADMIN_HEADERS)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "processing"
    assert body["productId"] == 9
    assert body["createdBy"] == "admin"
    assert ingested == [body["id"]]
    assert Path(settings.uploads_dir / "9" / f"{body['id']}.pdf").exists()

    listed = client.get("/documents", params={"productId": 9}, headers=USER_HEADERS)
    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [body["id"]]


def test_reindex_requires_existing_file(client, monkeypatch, tmp_path, db):
    settings = get_settings().model_copy(update={"data_dir": str(tmp_path / "data")})
    monkeypatch.setattr(documents_api, "get_settings", lambda: settings)
    document = InsuranceDocument(
        id="doc-missing",
        product_id=9,
        filename="policy.pdf",
        file_path=str(tmp_path / "gone.pdf"),
        file_size=10,
        status="ready",
        chunk_count=3,
        created_by="admin",
    )
    db.add(document)
    db.commit()

    response = client.post("/documents/doc-missing/reindex", headers=ADMIN_HEADERS)

    assert response.status_code == 409


def test_reindex_resets_status_and_schedules_ingest(client, monkeypatch, tmp_path, db):
    source = tmp_path / "policy.pdf"
    source.write_bytes(PDF_BYTES)
    document = InsuranceDocument(
        id="doc-ready",
        product_id=9,
        filename="policy.pdf",
        file_path=str(source),
        file_size=len(PDF_BYTES),
        status="ready",
        chunk_count=3,
        error_message="旧错误",
        created_by="admin",
    )
    db.add(document)
    db.commit()

    ingested: list[str] = []
    monkeypatch.setattr(documents_api, "run_ingest", lambda doc_id: ingested.append(doc_id))

    response = client.post("/documents/doc-ready/reindex", headers=ADMIN_HEADERS)

    assert response.status_code == 200
    assert response.json()["status"] == "processing"
    assert ingested == ["doc-ready"]


def test_delete_removes_record_file_and_vectors(client, monkeypatch, tmp_path, db):
    source = tmp_path / "policy.pdf"
    source.write_bytes(PDF_BYTES)
    document = InsuranceDocument(
        id="doc-del",
        product_id=9,
        filename="policy.pdf",
        file_path=str(source),
        file_size=len(PDF_BYTES),
        status="ready",
        chunk_count=1,
        created_by="admin",
    )
    db.add(document)
    db.commit()

    deleted: list[str] = []

    class FakeStore:
        def delete_by_doc(self, doc_id: str) -> None:
            deleted.append(doc_id)

    monkeypatch.setattr(documents_api, "get_vector_store", lambda: FakeStore())

    response = client.delete("/documents/doc-del", headers=ADMIN_HEADERS)

    assert response.status_code == 200
    assert deleted == ["doc-del"]
    assert not source.exists()
    db.expire_all()
    assert db.get(InsuranceDocument, "doc-del") is None


def test_delete_requires_admin(client):
    response = client.delete("/documents/doc-x", headers=USER_HEADERS)

    assert response.status_code == 403