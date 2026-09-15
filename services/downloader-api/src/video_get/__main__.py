from __future__ import annotations

import os

import uvicorn


def main() -> None:
    port = int(os.getenv("VIDEO_GET_PORT", "17382"))
    uvicorn.run("video_get.main:app", host="127.0.0.1", port=port, reload=False)


if __name__ == "__main__":
    main()
