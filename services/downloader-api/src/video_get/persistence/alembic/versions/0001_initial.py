"""Create the initial download jobs table."""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "download_jobs",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("source_url", sa.String(length=2048), nullable=False),
        sa.Column("canonical_url", sa.String(length=2048), nullable=False),
        sa.Column("platform", sa.String(length=32), nullable=False),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("selected_format_id", sa.String(length=256), nullable=True),
        sa.Column("title", sa.String(length=512), nullable=True),
        sa.Column("output_path", sa.String(length=2048), nullable=True),
        sa.Column("downloaded_bytes", sa.Integer(), nullable=False),
        sa.Column("total_bytes", sa.Integer(), nullable=True),
        sa.Column("estimated_total_bytes", sa.Integer(), nullable=True),
        sa.Column("speed_bytes_per_second", sa.Float(), nullable=True),
        sa.Column("eta_seconds", sa.Float(), nullable=True),
        sa.Column("progress", sa.Float(), nullable=True),
        sa.Column("error_code", sa.String(length=64), nullable=True),
        sa.Column("error_message", sa.String(length=1024), nullable=True),
        sa.Column("retry_of_job_id", sa.String(length=64), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_download_jobs_platform", "download_jobs", ["platform"])
    op.create_index("ix_download_jobs_state", "download_jobs", ["state"])


def downgrade() -> None:
    op.drop_index("ix_download_jobs_state", table_name="download_jobs")
    op.drop_index("ix_download_jobs_platform", table_name="download_jobs")
    op.drop_table("download_jobs")
