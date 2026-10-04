"""Presentation of real yt-dlp progress; unknown totals stay unknown."""

from __future__ import annotations

import math

STATES = {
    "queued": "排队中",
    "analyzing": "分析中",
    "downloading": "下载中",
    "processing": "处理中",
    "completed": "已完成",
    "failed": "失败",
    "cancelled": "已取消",
}


def number(value):
    if isinstance(value, (int, float)) and math.isfinite(value) and value >= 0:
        return value
    return None


def size(value):
    value = number(value) or 0
    for unit in ("B", "KiB", "MiB", "GiB", "TiB"):
        if value < 1024 or unit == "TiB":
            return f"{value:.1f} {unit}" if unit != "B" else f"{value:.0f} B"
        value /= 1024


def percent(job):
    if job["state"] == "completed":
        return 100.0
    progress = job.get("progress") or {}
    ratio = number(progress.get("progress"))
    total = number(progress.get("total_bytes")) or number(progress.get("estimated_total_bytes"))
    if ratio is None and total:
        ratio = (number(progress.get("downloaded_bytes")) or 0) / total
    return min(100.0, ratio * 100) if ratio is not None else None


def task_status(job):
    label = STATES.get(job["state"], job["state"])
    value = percent(job)
    if job["state"] == "downloading" and value is not None:
        estimated = not (job.get("progress") or {}).get("total_bytes")
        return f"{label} {'约 ' if estimated else ''}{value:.1f}%"
    return label


def progress_text(job):
    if job.get("error_message"):
        return job["error_message"]
    if job["state"] == "processing":
        return "正在处理文件，请稍候…"
    if job["state"] != "downloading":
        return task_status(job)
    progress = job.get("progress") or {}
    total = number(progress.get("total_bytes"))
    estimated = number(progress.get("estimated_total_bytes"))
    downloaded = size(progress.get("downloaded_bytes"))
    amount = (
        f"{downloaded} / {size(total)}"
        if total
        else (
            f"{downloaded} / 约 {size(estimated)}"
            if estimated
            else f"已下载 {downloaded}（大小未知）"
        )
    )
    parts = [task_status(job), amount]
    speed = number(progress.get("speed_bytes_per_second"))
    if speed is not None:
        parts.append(size(speed) + "/s")
    eta = number(progress.get("eta_seconds"))
    if eta is not None:
        seconds = int(eta)
        minutes, seconds = divmod(seconds, 60)
        hours, minutes = divmod(minutes, 60)
        remaining = (
            f"{hours}小时{minutes}分"
            if hours
            else (f"{minutes}分{seconds}秒" if minutes else f"{seconds}秒")
        )
        parts.append("剩余约 " + remaining)
    return " · ".join(parts)
