from __future__ import annotations

import uvicorn


def main() -> None:
    uvicorn.run("video_get.main:app", host="127.0.0.1", port=17382, reload=False)


if __name__ == "__main__":
    main()
