# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
import sys
from PyInstaller.building.datastruct import Tree
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

root = Path(SPECPATH).parents[1]
sys.setrecursionlimit(10_000)
sys.path.insert(0, str(root / "services" / "downloader-api" / "src"))
sys.path.insert(0, str(root / "apps" / "desktop-companion" / "src"))

a = Analysis(
    [str(root / "apps" / "desktop-companion" / "src" / "video_get_companion" / "__main__.py")],
    pathex=[
        str(root / "apps" / "desktop-companion" / "src"),
        str(root / "services" / "downloader-api" / "src"),
    ],
    datas=collect_data_files("video_get") + collect_data_files("yt_dlp"),
    hiddenimports=(
        sorted(collect_submodules("video_get"))
        + sorted(collect_submodules("yt_dlp.extractor"))
        + [
            "uvicorn.logging",
            "uvicorn.loops.auto",
            "uvicorn.protocols.http.auto",
            "uvicorn.protocols.websockets.auto",
            "uvicorn.lifespan.on",
        ]
    ),
    excludes=[
        "IPython",
        "jedi",
        "matplotlib",
        "nbformat",
        "numpy",
        "pandas",
        "pytest",
        "scipy",
        "torch",
        "zmq",
    ],
)
a.datas += Tree(
    str(
        root
        / "services"
        / "downloader-api"
        / "src"
        / "video_get"
        / "persistence"
        / "alembic"
    ),
    prefix="video_get/persistence/alembic",
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="VideoGet",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
)
