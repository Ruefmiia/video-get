from __future__ import annotations

import os
import sys
import threading
import tkinter as tk
import winreg
from pathlib import Path
from tkinter import messagebox

import pystray
from PIL import Image, ImageDraw

from .service import ServiceController
from .single_instance import SingleInstance


class CompanionApp:
    def __init__(self, data_dir: Path | None = None) -> None:
        self.data_dir = data_dir or Path(os.getenv("LOCALAPPDATA", ".")) / "VideoGet"
        self.controller = ServiceController(self.data_dir)
        self.instance = SingleInstance()
        self.root = tk.Tk()
        self.root.title("Video Get")
        self.root.geometry("520x350")
        self.root.minsize(480, 330)
        self.root.protocol("WM_DELETE_WINDOW", self.hide_window)
        self.status_text = tk.StringVar(value="正在检查本地服务…")
        self.detail_text = tk.StringVar(value="127.0.0.1:17382")
        self.startup_enabled = tk.BooleanVar(value=self._startup_is_enabled())
        self.tray: pystray.Icon | None = None
        self._build_ui()

    def _build_ui(self) -> None:
        self.root.configure(bg="#f8fafc")
        frame = tk.Frame(self.root, bg="#f8fafc", padx=30, pady=26)
        frame.pack(fill="both", expand=True)
        tk.Label(
            frame,
            text="VIDEO GET · DESKTOP",
            fg="#1d4ed8",
            bg="#f8fafc",
            font=("Segoe UI", 10, "bold"),
        ).pack(anchor="w")
        tk.Label(
            frame, text="本地下载服务", fg="#1e293b", bg="#f8fafc", font=("Segoe UI", 24, "bold")
        ).pack(anchor="w", pady=(7, 20))
        card = tk.Frame(
            frame, bg="white", highlightbackground="#e2e8f0", highlightthickness=1, padx=20, pady=18
        )
        card.pack(fill="x")
        self.status_label = tk.Label(
            card,
            textvariable=self.status_text,
            fg="#9a3412",
            bg="white",
            font=("Segoe UI", 14, "bold"),
        )
        self.status_label.pack(anchor="w")
        tk.Label(
            card, textvariable=self.detail_text, fg="#475569", bg="white", font=("Consolas", 10)
        ).pack(anchor="w", pady=(5, 14))
        actions = tk.Frame(card, bg="white")
        actions.pack(fill="x")
        for label, command in (
            ("启动服务", self.start_service),
            ("停止服务", self.stop_service),
            ("打开下载目录", self.open_downloads),
        ):
            tk.Button(actions, text=label, command=command, padx=12, pady=8, cursor="hand2").pack(
                side="left", padx=(0, 8)
            )
        utilities = tk.Frame(frame, bg="#f8fafc")
        utilities.pack(anchor="w", pady=(18, 0))
        tk.Button(
            utilities, text="复制访问令牌", command=self.copy_token, padx=12, pady=8, cursor="hand2"
        ).pack(side="left", padx=(0, 8))
        tk.Button(
            utilities,
            text="复制诊断信息",
            command=self.copy_diagnostics,
            padx=12,
            pady=8,
            cursor="hand2",
        ).pack(side="left")
        tk.Checkbutton(
            frame,
            text="登录 Windows 后自动启动",
            variable=self.startup_enabled,
            command=self.toggle_startup,
            bg="#f8fafc",
            fg="#1e293b",
            activebackground="#f8fafc",
            cursor="hand2",
        ).pack(anchor="w", pady=(16, 0))

    def start_service(self) -> None:
        self.status_text.set("正在启动本地服务…")
        threading.Thread(target=self._start_worker, daemon=True).start()

    def _start_worker(self) -> None:
        try:
            self.controller.start()
            self.root.after(0, lambda: self._set_status(True))
        except RuntimeError as error:
            message = str(error)
            self.root.after(0, lambda: self._show_error(message))

    def stop_service(self) -> None:
        self.controller.stop()
        self._set_status(False)

    def _set_status(self, running: bool) -> None:
        self.status_text.set("服务运行正常" if running else "服务已停止")
        self.status_label.configure(fg="#166534" if running else "#9a3412")
        if self.tray:
            self.tray.title = f"Video Get · {'运行中' if running else '已停止'}"

    def _show_error(self, message: str) -> None:
        self._set_status(False)
        messagebox.showerror("Video Get", message, parent=self.root)

    def open_downloads(self) -> None:
        self.controller.download_dir.mkdir(parents=True, exist_ok=True)
        os.startfile(self.controller.download_dir)

    def copy_diagnostics(self) -> None:
        self.root.clipboard_clear()
        self.root.clipboard_append(self.controller.diagnostics())
        self.status_text.set("诊断信息已复制（不含令牌和媒体链接）")

    def copy_token(self) -> None:
        token_path = self.data_dir / "api-token"
        if not token_path.exists():
            self._show_error("令牌尚未生成，请先启动服务。")
            return
        token = token_path.read_text(encoding="utf-8").strip()
        self.root.clipboard_clear()
        self.root.clipboard_append(token)
        self.status_text.set("访问令牌已复制，请粘贴到扩展设置")

    def _startup_is_enabled(self) -> bool:
        try:
            with winreg.OpenKey(
                winreg.HKEY_CURRENT_USER,
                r"Software\Microsoft\Windows\CurrentVersion\Run",
            ) as key:
                winreg.QueryValueEx(key, "VideoGet")
            return True
        except OSError:
            return False

    def toggle_startup(self) -> None:
        key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
        try:
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, key_path) as key:
                if self.startup_enabled.get():
                    executable = Path(sys.executable).resolve()
                    winreg.SetValueEx(key, "VideoGet", 0, winreg.REG_SZ, f'"{executable}"')
                    self.status_text.set("已启用开机启动")
                else:
                    try:
                        winreg.DeleteValue(key, "VideoGet")
                    except FileNotFoundError:
                        pass
                    self.status_text.set("已关闭开机启动")
        except OSError as error:
            self.startup_enabled.set(not self.startup_enabled.get())
            self._show_error(f"无法更新开机启动设置：{error}")

    def show_window(self) -> None:
        self.root.after(0, self.root.deiconify)

    def hide_window(self) -> None:
        self.root.withdraw()

    def quit(self) -> None:
        self.controller.stop()
        if self.tray:
            self.tray.stop()
        self.instance.release()
        self.root.after(0, self.root.destroy)

    def run(self) -> int:
        if not self.instance.acquire():
            messagebox.showinfo("Video Get", "Video Get 已在运行。", parent=self.root)
            self.root.destroy()
            return 0
        self.tray = pystray.Icon(
            "video-get",
            _tray_image(),
            "Video Get · 正在启动",
            menu=pystray.Menu(
                pystray.MenuItem("打开状态", lambda _icon, _item: self.show_window(), default=True),
                pystray.MenuItem("打开下载目录", lambda _icon, _item: self.open_downloads()),
                pystray.MenuItem("退出", lambda _icon, _item: self.quit()),
            ),
        )
        threading.Thread(target=self.tray.run, daemon=True).start()
        self.start_service()
        self.root.mainloop()
        return 0


def _tray_image() -> Image.Image:
    image = Image.new("RGBA", (64, 64), "#be123c")
    draw = ImageDraw.Draw(image)
    draw.line((32, 13, 32, 40), fill="white", width=7)
    draw.line((20, 30, 32, 42, 44, 30), fill="white", width=7, joint="curve")
    draw.line((16, 50, 48, 50), fill="white", width=6)
    return image
