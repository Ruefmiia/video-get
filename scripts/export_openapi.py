from pathlib import Path

from video_get.main import export_openapi

if __name__ == "__main__":
    export_openapi(Path("contracts/openapi/openapi.json"))
