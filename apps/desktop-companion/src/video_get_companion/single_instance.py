from __future__ import annotations

import ctypes
import sys


class SingleInstance:
    def __init__(self, name: str = "Local\\VideoGetCompanion") -> None:
        self.handle: int | None = None
        self.name = name

    def acquire(self) -> bool:
        if sys.platform != "win32":
            return True
        kernel32 = ctypes.windll.kernel32
        self.handle = kernel32.CreateMutexW(None, False, self.name)
        return bool(self.handle) and kernel32.GetLastError() != 183

    def release(self) -> None:
        if self.handle and sys.platform == "win32":
            ctypes.windll.kernel32.CloseHandle(self.handle)
            self.handle = None
