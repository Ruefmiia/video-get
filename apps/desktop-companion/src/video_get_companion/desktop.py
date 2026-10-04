from __future__ import annotations

import os
import queue
import subprocess
import sys
import threading
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import messagebox, ttk

from .app import CompanionApp
from .client import DesktopApi, download_payload
from .progress import percent, progress_text, task_status
from .service import stop_owned_process

STATES = {
    "queued": "排队中",
    "analyzing": "分析中",
    "downloading": "下载中",
    "processing": "合并中",
    "completed": "已完成",
    "failed": "失败",
    "cancelled": "已取消",
}


class DesktopApp(CompanionApp):
    """Native standalone UI; extension and desktop share jobs and service."""

    def _build_ui(self):
        self.api = DesktopApi(self.data_dir, self.controller.port)
        self.events = queue.Queue()
        self.closed = False
        self.ready = False
        self.busy = False
        self.polling = False
        self.media = None
        self.jobs = {}
        self.generation = 0
        self.login_process = None
        self.settings_window = None
        self.starting = False
        self.root.geometry("800x740")
        self.root.minsize(680, 660)
        self.root.configure(bg="#fafafa")
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TFrame", background="#fafafa")
        style.configure(
            "TLabel", background="#fafafa", foreground="#18181b", font=("Microsoft YaHei UI", 10)
        )
        style.configure("TButton", padding=(12, 9), font=("Microsoft YaHei UI", 10))
        style.configure("Primary.TButton", background="#18181b", foreground="white")
        style.map(
            "Primary.TButton",
            background=[("disabled", "#dedede"), ("active", "#3f3f46")],
            foreground=[("disabled", "#666666")],
        )
        style.configure("Treeview", rowheight=32, font=("Microsoft YaHei UI", 10))
        style.configure("Horizontal.TProgressbar", background="#18181b")
        frame = ttk.Frame(self.root, padding=24)
        frame.pack(fill="both", expand=True)
        header = ttk.Frame(frame)
        header.pack(fill="x", pady=(0, 20))
        ttk.Label(header, text="Video Get", font=("Segoe UI", 25, "bold")).pack(side="left")
        ttk.Button(header, text="设置", command=self.settings).pack(side="right")
        self.login_button = ttk.Button(header, text="B站登录", command=self.open_bilibili_login)
        self.login_button.pack(side="right", padx=8)
        self.status_label = ttk.Label(frame, textvariable=self.status_text)
        self.status_label.pack(anchor="w", pady=(0, 16))
        ttk.Label(frame, text="视频链接").pack(anchor="w", pady=(0, 8))
        self.url = tk.StringVar()
        self.url.trace_add("write", self._invalidate)
        self.entry = ttk.Entry(frame, textvariable=self.url, font=("Microsoft YaHei UI", 11))
        self.entry.pack(fill="x", ipady=9)
        self.entry.bind("<Return>", lambda event: self.analyze())
        actions = ttk.Frame(frame)
        actions.pack(fill="x", pady=12)
        ttk.Button(actions, text="粘贴", command=self.paste).pack(side="left")
        ttk.Button(actions, text="清空", command=lambda: self.url.set("")).pack(side="left", padx=8)
        self.analyze_button = ttk.Button(
            actions,
            text="分析链接",
            style="Primary.TButton",
            command=self.analyze,
            state="disabled",
        )
        self.analyze_button.pack(side="right")
        self.notice = tk.StringVar(value="粘贴链接，开始下载")
        self.notice_label = ttk.Label(frame, textvariable=self.notice, wraplength=700)
        self.notice_label.pack(fill="x", pady=(8, 12))
        self.root.bind(
            "<Configure>",
            lambda e: self.notice_label.configure(
                wraplength=max(400, self.root.winfo_width() - 60)
            ),
        )
        choices = ttk.Frame(frame)
        choices.pack(fill="x")
        choices.columnconfigure(0, weight=1)
        choices.columnconfigure(1, weight=2)
        ttk.Label(choices, text="视频").grid(row=0, column=0, sticky="w", pady=(0, 6))
        ttk.Label(choices, text="画质").grid(row=0, column=1, sticky="w", padx=(12, 0))
        self.asset = ttk.Combobox(choices, state="disabled")
        self.asset.grid(row=1, column=0, sticky="ew")
        self.asset.bind("<<ComboboxSelected>>", self._formats)
        self.format = ttk.Combobox(choices, state="disabled")
        self.format.grid(row=1, column=1, sticky="ew", padx=(12, 0))
        self.download_button = ttk.Button(
            frame, text="下载", style="Primary.TButton", command=self.download, state="disabled"
        )
        self.download_button.pack(fill="x", pady=16)
        ttk.Separator(frame).pack(fill="x", pady=(0, 16))
        row = ttk.Frame(frame)
        row.pack(fill="x", pady=(0, 8))
        ttk.Label(row, text="最近下载", font=("Microsoft YaHei UI", 11, "bold")).pack(side="left")
        ttk.Button(row, text="打开下载目录", command=self.open_downloads).pack(side="right")
        area = ttk.Frame(frame)
        area.pack(fill="both", expand=True)
        self.tree = ttk.Treeview(
            area, columns=("title", "state"), show="headings", selectmode="browse", height=5
        )
        self.tree.heading("title", text="名称")
        self.tree.heading("state", text="状态")
        self.tree.column("title", width=480, minwidth=200)
        self.tree.column("state", width=160, stretch=False)
        scroll = ttk.Scrollbar(area, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.tree.pack(fill="both", expand=True)
        self.tree.bind("<<TreeviewSelect>>", self._selected)
        self.progress = ttk.Progressbar(frame, maximum=100)
        self.progress.pack(fill="x", pady=(12, 6))
        self.job_notice = tk.StringVar(value="暂无下载任务")
        ttk.Label(frame, textvariable=self.job_notice, wraplength=620).pack(anchor="w")
        bottom = ttk.Frame(frame)
        bottom.pack(fill="x", pady=(8, 0))
        self.cancel_button = ttk.Button(
            bottom, text="取消下载", command=lambda: self.job_action("DELETE"), state="disabled"
        )
        self.cancel_button.pack(side="left")
        self.retry_button = ttk.Button(
            bottom, text="重试", command=lambda: self.job_action("POST"), state="disabled"
        )
        self.retry_button.pack(side="left", padx=8)
        self.open_button = ttk.Button(
            bottom, text="打开文件", command=self.open_file, state="disabled"
        )
        self.open_button.pack(side="right")
        self.root.after(100, self._pump)
        self.root.after(1500, self._poll)

    def _async(self, work, done, failed=None):
        def worker():
            try:
                result = work()
                self.events.put(lambda: done(result))
            except Exception as error:
                message = str(error)
                self.events.put(lambda: (failed or self._error)(message))

        threading.Thread(target=worker, daemon=True).start()

    def _pump(self):
        if self.closed:
            return
        while not self.events.empty():
            self.events.get_nowait()()
        self.root.after(100, self._pump)

    def _error(self, message):
        self.busy = False
        self.notice.set(message)
        self._buttons()

    def _buttons(self):
        self.analyze_button.configure(
            state="normal" if self.ready and not self.busy else "disabled"
        )
        usable = self.media and self.asset.current() >= 0 and self.format.current() >= 0
        self.download_button.configure(
            state="normal" if self.ready and not self.busy and usable else "disabled"
        )

    def _invalidate(self, *_):
        self.generation += 1
        self.media = None
        self.asset.configure(values=[], state="disabled")
        self.format.configure(values=[], state="disabled")
        self.asset.set("")
        self.format.set("")
        self._buttons()

    def paste(self):
        try:
            self.url.set(self.root.clipboard_get().strip())
        except tk.TclError:
            self.notice.set("剪贴板没有可粘贴的链接")

    def analyze(self):
        if not self.ready or self.busy:
            return
        url = self.url.get().strip()
        if not url:
            self.notice.set("请先填写视频链接")
            self.entry.focus_set()
            return
        self.busy = True
        self.media = None
        generation = self.generation
        self.notice.set("正在分析链接…")
        self._buttons()

        def done(media):
            self.busy = False
            if generation != self.generation:
                self.notice.set("链接已变更，请重新分析")
                self._buttons()
                return
            self.media = media
            assets = media.get("assets", [])
            self.asset.configure(
                values=[f"视频 {i + 1}" for i in range(len(assets))],
                state="readonly" if assets else "disabled",
            )
            self.notice.set(media.get("title") or "链接分析完成")
            if assets:
                self.asset.current(0)
                self._formats()
            else:
                self._error("未找到可下载的视频")

        self._async(lambda: self.api.request("/analyze", "POST", {"url": url}), done)

    def _formats(self, *_):
        formats = self.media["assets"][self.asset.current()]["formats"]
        self.format.configure(
            values=[f["label"] for f in formats], state="readonly" if formats else "disabled"
        )
        self.format.set("")
        if formats:
            self.format.current(0)
        self._buttons()

    def download(self):
        if self.busy or not self.media or self.format.current() < 0:
            return
        payload = download_payload(self.media, self.asset.current(), self.format.current())
        self.busy = True
        self._buttons()
        self._async(lambda: self.api.request("/downloads", "POST", payload), self._new_job)

    def _new_job(self, job):
        self.busy = False
        self.notice.set("已添加下载任务")
        self._buttons()
        self._render_jobs([job] + [j for j in self.jobs.values() if j["id"] != job["id"]])
        self.tree.selection_set(job["id"])
        self.tree.see(job["id"])
        self._selected()

    def _poll(self):
        if self.closed:
            return
        if self.ready and not self.polling:
            self.polling = True

            def done(jobs):
                self.polling = False
                self._render_jobs(jobs)

            def failed(message):
                self.polling = False
                self.status_text.set(message)

            self._async(lambda: self.api.request("/downloads?limit=50"), done, failed)
        self.root.after(1500, self._poll)

    def _render_jobs(self, jobs):
        self.jobs = {j["id"]: j for j in jobs[:50]}
        for item in self.tree.get_children():
            if item not in self.jobs:
                self.tree.delete(item)
        for i, job in enumerate(self.jobs.values()):
            values = (job.get("title") or job["source_url"], task_status(job))
            if self.tree.exists(job["id"]):
                self.tree.item(job["id"], values=values)
                self.tree.move(job["id"], "", i)
            else:
                self.tree.insert("", i, iid=job["id"], values=values)
        if not self.tree.selection() and self.jobs:
            self.tree.selection_set(next(iter(self.jobs)))
        self._selected()

    def _job(self):
        selection = self.tree.selection()
        return self.jobs.get(selection[0]) if selection else None

    def _selected(self, *_):
        job = self._job()
        if not job:
            self.progress["value"] = 0
            self.job_notice.set("暂无下载任务")
            for button in (self.cancel_button, self.retry_button, self.open_button):
                button.configure(state="disabled")
            return
        state = job["state"]
        self.progress["value"] = percent(job) or 0
        self.job_notice.set(progress_text(job))
        self.cancel_button.configure(
            state="normal"
            if state in {"queued", "analyzing", "downloading", "processing"}
            else "disabled"
        )
        self.retry_button.configure(
            state="normal" if state in {"failed", "cancelled"} else "disabled"
        )
        self.open_button.configure(
            state="normal" if state == "completed" and job.get("output_path") else "disabled"
        )

    def job_action(self, method):
        job = self._job()
        if job:
            path = "/downloads/" + job["id"] + ("/retry" if method == "POST" else "")
            self._async(lambda: self.api.request(path, method), self._new_job)

    def open_file(self):
        job = self._job()
        path = Path(job["output_path"]) if job and job.get("output_path") else None
        if (
            path
            and path.is_file()
            and path.resolve().is_relative_to(self.controller.download_dir.resolve())
        ):
            os.startfile(path)
        else:
            self.job_notice.set("文件已移动或不存在，请打开下载目录检查")

    def _start_worker(self):
        try:
            self.controller.start()
            self.events.put(lambda: self._set_status(True))
        except Exception as error:
            message = str(error)
            self.events.put(lambda: self._start_failed(message))

    def start_service(self):
        if self.starting:
            return
        self.starting = True
        super().start_service()

    def _start_failed(self, message):
        self._set_status(False)
        self._error(message)

    def _set_status(self, running):
        self.starting = False
        self.ready = running
        self.status_text.set("服务已就绪" if running else "服务已停止")
        self._buttons()
        if running:
            self._async(
                lambda: self.api.request("/sessions/bilibili"),
                lambda s: self.login_button.configure(
                    text="B站已连接" if s["configured"] else "B站登录"
                ),
            )

    def stop_service(self):
        self._async(self.controller.stop, lambda _: self._set_status(False))

    def settings(self):
        if self.settings_window and self.settings_window.winfo_exists():
            self.settings_window.lift()
            return
        window = tk.Toplevel(self.root)
        self.settings_window = window
        window.title("Video Get · 设置")
        frame = ttk.Frame(window, padding=24)
        frame.pack(fill="both", expand=True)
        for label, command in [
            ("启动服务", self.start_service),
            ("停止服务", self.stop_service),
            ("复制扩展访问令牌", self.copy_token),
            ("复制诊断信息", self.copy_diagnostics),
            ("清除 B站登录状态", self.clear_login),
        ]:
            ttk.Button(frame, text=label, command=command).pack(fill="x", pady=4)
        ttk.Checkbutton(
            frame,
            text="登录 Windows 后自动启动",
            variable=self.startup_enabled,
            command=self.toggle_startup,
        ).pack(pady=12)
        ttk.Button(frame, text="退出 Video Get", command=self.quit).pack(fill="x")

    def clear_login(self):
        if self.login_process:
            self.notice.set("请先关闭 B站登录窗口，再清除登录状态")
            return
        self._async(
            lambda: self.api.request("/sessions/bilibili", "DELETE"),
            lambda _: self._login_changed(False),
        )

    def _login_changed(self, configured):
        self.login_button.configure(text="B站已连接" if configured else "B站登录", state="normal")
        self._invalidate()
        self.notice.set("登录状态已更新，请重新分析链接" if configured else "已清除 B站登录状态")

    def open_bilibili_login(self):
        if not self.ready or self.login_process:
            return
        command = (
            [sys.executable]
            if getattr(sys, "frozen", False)
            else [sys.executable, "-m", "video_get_companion"]
        )
        env = os.environ.copy()
        env["VIDEO_GET_DATA_DIR"] = str(self.data_dir.resolve())
        env["VIDEO_GET_PORT"] = str(self.controller.port)
        try:
            self.login_process = subprocess.Popen(
                command + ["--login-bilibili"],
                env=env,
                creationflags=subprocess.CREATE_NO_WINDOW,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except OSError:
            self._error("无法打开登录窗口")
            return
        self.login_button.configure(state="disabled")
        self.notice.set("请在登录窗口登录 B站，成功后自动返回")
        self.root.after(500, self._wait_login)

    def _wait_login(self):
        code = self.login_process.poll()
        if code is None:
            self.root.after(500, self._wait_login)
            return
        self.login_process = None
        self.login_button.configure(state="normal")
        if code == 0:
            self._login_changed(True)
            self.root.deiconify()
            self.root.lift()
        elif code == 3:
            self.notice.set("登录窗口无法启动，请安装 Microsoft Edge WebView2 Runtime 后重试")
            if messagebox.askyesno(
                "登录组件", "是否打开微软 WebView2 官方下载页面？", parent=self.root
            ):
                webbrowser.open("https://developer.microsoft.com/microsoft-edge/webview2/")
        else:
            self.notice.set("登录已取消或失败，可重新尝试")

    def show_window(self):
        self.events.put(self.root.deiconify)

    def quit(self):
        if threading.current_thread() is not threading.main_thread():
            self.events.put(self.quit)
            return
        self.closed = True
        if self.login_process and self.login_process.poll() is None:
            stop_owned_process(self.login_process)
        if self.tray:
            self.tray.stop()
        self.controller.stop()
        self.instance.release()
        self.root.destroy()
