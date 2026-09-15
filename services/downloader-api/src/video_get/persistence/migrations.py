from __future__ import annotations

from pathlib import Path

from alembic import command
from alembic.config import Config


def upgrade_database(database_path: Path) -> None:
    config = Config()
    config.set_main_option("script_location", str(Path(__file__).with_name("alembic").resolve()))
    config.set_main_option("sqlalchemy.url", f"sqlite:///{database_path.as_posix()}")
    command.upgrade(config, "head")
