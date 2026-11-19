"""身份依赖：只信任网关注入的 X-User-* 头，可选校验网关 HMAC 签名。

签名算法与后端 ecommerce-common 的 GatewaySignature 完全一致：
    payload = f"{userId}|{username}|{role}|{timestamp}"
    signature = hex(HMAC_SHA256(secret, payload))
"""

from __future__ import annotations

import hashlib
import hmac
import time
from dataclasses import dataclass

from fastapi import Depends, Header, HTTPException, status

from .config import get_settings

HEADER_USER_ID = "X-User-Id"
HEADER_USER_NAME = "X-User-Name"
HEADER_USER_ROLE = "X-User-Role"
HEADER_GATEWAY_TIMESTAMP = "X-Gateway-Timestamp"
HEADER_GATEWAY_SIGNATURE = "X-Gateway-Signature"


@dataclass(frozen=True)
class CurrentUser:
    user_id: int
    username: str
    role: str

    @property
    def is_admin(self) -> bool:
        return self.role.upper() == "ADMIN"


def _verify_signature(user_id: str, username: str, role: str, timestamp: str, signature: str) -> bool:
    settings = get_settings()
    secret = settings.gateway_jwt_secret
    if not secret:
        return False
    payload = f"{user_id}|{username}|{role}|{timestamp}"
    expected = hmac.new(
        secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    if not hmac.compare_digest(expected, signature):
        return False
    try:
        skew = abs(int(time.time()) - int(timestamp))
    except ValueError:
        return False
    return skew <= settings.gateway_max_skew_seconds


def require_user(
    x_user_id: str | None = Header(default=None, alias=HEADER_USER_ID),
    x_user_name: str | None = Header(default=None, alias=HEADER_USER_NAME),
    x_user_role: str | None = Header(default=None, alias=HEADER_USER_ROLE),
    x_gateway_timestamp: str | None = Header(default=None, alias=HEADER_GATEWAY_TIMESTAMP),
    x_gateway_signature: str | None = Header(default=None, alias=HEADER_GATEWAY_SIGNATURE),
) -> CurrentUser:
    """解析当前用户；缺少身份头或验签失败返回 401。"""
    if not x_user_id or not x_user_id.isdigit() or not x_user_name:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="缺少网关身份信息，请通过 /api/ai/** 访问",
        )

    role = (x_user_role or "USER").upper()

    settings = get_settings()
    if settings.verify_gateway_signature:
        if not x_gateway_timestamp or not x_gateway_signature or not _verify_signature(
            x_user_id, x_user_name, role, x_gateway_timestamp, x_gateway_signature
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED, detail="网关身份凭据无效"
            )

    return CurrentUser(user_id=int(x_user_id), username=x_user_name, role=role)


def require_admin(user: CurrentUser) -> CurrentUser:
    if not user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="仅管理员可维护保险知识库")
    return user


def admin_only(user: CurrentUser = Depends(require_user)) -> CurrentUser:
    """FastAPI 依赖：先解析用户，再要求 ADMIN。"""
    return require_admin(user)