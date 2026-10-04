import pytest
from video_get_companion.progress import percent, progress_text, task_status


def job(**progress):
    return {"state": "downloading", "progress": progress}


def test_exact_progress_with_speed_and_eta():
    value = job(
        downloaded_bytes=10 * 1024**2,
        total_bytes=20 * 1024**2,
        speed_bytes_per_second=2 * 1024**2,
        eta_seconds=65,
        progress=0.5,
    )
    assert percent(value) == 50
    assert task_status(value) == "下载中 50.0%"
    assert progress_text(value) == "下载中 50.0% · 10.0 MiB / 20.0 MiB · 2.0 MiB/s · 剩余约 1分5秒"


def test_estimated_and_missing_totals():
    value = job(downloaded_bytes=1024, estimated_total_bytes=2048)
    assert percent(value) == 50
    assert "约 50.0%" in task_status(value)
    assert "约 2.0 KiB" in progress_text(value)
    value = job(downloaded_bytes=1024)
    assert percent(value) is None
    assert "%" not in progress_text(value)
    assert "大小未知" in progress_text(value)


@pytest.mark.parametrize("ratio", [float("nan"), float("inf"), -1, None])
def test_invalid_progress_not_fabricated(ratio):
    assert percent(job(progress=ratio)) is None


def test_terminal_and_processing_states():
    assert percent({"state": "completed"}) == 100
    assert progress_text({"state": "completed"}) == "已完成"
    assert progress_text({"state": "processing"}) == "正在处理文件，请稍候…"
    assert progress_text({"state": "failed", "error_message": "连接失败"}) == "连接失败"
    assert percent(job(progress=2)) == 100
