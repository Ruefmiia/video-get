from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

_SENSITIVE_KEYS = {
    "authorization",
    "cookie",
    "set-cookie",
    "token",
    "access_token",
    "auth_token",
    "sessionid",
    "sig",
    "signature",
}
_BEARER_PATTERN = re.compile(r"(?i)bearer\s+[A-Za-z0-9._~+/=-]+")


def redact_url(value: str) -> str:
    try:
        parsed = urlsplit(value)
    except ValueError:
        return "[REDACTED_URL]"
    query = [
        (key, "[REDACTED]" if key.lower() in _SENSITIVE_KEYS else item)
        for key, item in parse_qsl(parsed.query, keep_blank_values=True)
    ]
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, urlencode(query), ""))


def redact_text(value: str) -> str:
    return _BEARER_PATTERN.sub("Bearer [REDACTED]", value)


def redact_mapping(values: Mapping[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in values.items():
        if key.lower() in _SENSITIVE_KEYS:
            result[key] = "[REDACTED]"
        elif isinstance(value, Mapping):
            result[key] = redact_mapping(value)
        elif isinstance(value, str) and value.startswith(("http://", "https://")):
            result[key] = redact_url(value)
        elif isinstance(value, str):
            result[key] = redact_text(value)
        else:
            result[key] = value
    return result
