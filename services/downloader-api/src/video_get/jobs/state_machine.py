from __future__ import annotations

from video_get.domain.enums import JobState

ALLOWED_TRANSITIONS: dict[JobState, frozenset[JobState]] = {
    JobState.QUEUED: frozenset({JobState.ANALYZING, JobState.CANCELLED, JobState.FAILED}),
    JobState.ANALYZING: frozenset({JobState.DOWNLOADING, JobState.CANCELLED, JobState.FAILED}),
    JobState.DOWNLOADING: frozenset({JobState.PROCESSING, JobState.CANCELLED, JobState.FAILED}),
    JobState.PROCESSING: frozenset({JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED}),
    JobState.COMPLETED: frozenset(),
    JobState.FAILED: frozenset(),
    JobState.CANCELLED: frozenset(),
}


class InvalidJobTransition(ValueError):
    def __init__(self, current: JobState, target: JobState) -> None:
        super().__init__(f"Invalid download job transition: {current.value} -> {target.value}")
        self.current = current
        self.target = target


def validate_transition(current: JobState, target: JobState) -> None:
    if target not in ALLOWED_TRANSITIONS[current]:
        raise InvalidJobTransition(current, target)
