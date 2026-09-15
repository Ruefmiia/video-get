# Third-party notices

Video Get desktop companion includes open-source Python packages. Release builds must retain the license files supplied by each dependency.

| Component | Purpose | License family |
|---|---|---|
| FastAPI / Starlette / Uvicorn | Local HTTP API | MIT / BSD |
| yt-dlp | Media provider integration | Unlicense |
| SQLAlchemy / Alembic | SQLite persistence and migrations | MIT |
| pystray | Windows tray integration | LGPL-3.0 |
| Pillow | Tray image generation | HPND |
| PyInstaller | Windows executable packaging | GPL-2.0 with bootloader exception |

The Windows installer redistributes the standalone `ffmpeg.exe` and `ffprobe.exe` programs from the gyan.dev FFmpeg 8.0.1 essentials build. That build is licensed under GNU GPL version 3. Its original `LICENSE` and `README.txt` files are installed in the `ffmpeg` directory alongside the programs. FFmpeg source and build information are available from the URLs in that README.
