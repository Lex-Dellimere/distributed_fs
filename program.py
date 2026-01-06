import subprocess
import os
import sys
import tkinter as tk
from tkinter import scrolledtext, ttk, messagebox
import threading

class ClientWindow(tk.Toplevel):
    def __init__(self, master, client_id):
        super().__init__(master)
        self.title(f"DFS Client {client_id}")
        self.geometry("600x450")
        self.client_id = client_id
        self.process = None
        self.setup_ui()
        self.master.master.app.center_window(self) if hasattr(self.master.master, 'app') else None
        self.protocol("WM_DELETE_WINDOW", self.stop_client)
        self.start_client_process()

    def stop_client(self):
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
        if self.winfo_exists():
            self.destroy()

    def setup_ui(self):
        self.terminal = scrolledtext.ScrolledText(self, state=tk.DISABLED, bg="#1e1e1e", fg="#00ff00", insertbackground="white", font=("Consolas", 10))
        self.terminal.pack(expand=True, fill=tk.BOTH, padx=5, pady=5)

        input_frame = ttk.Frame(self, padding="5")
        input_frame.pack(fill=tk.X)

        ttk.Label(input_frame, text="CMD>").pack(side=tk.LEFT)

        self.entry = ttk.Entry(input_frame, font=("Consolas", 11))
        self.entry.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=5)
        self.entry.bind("<Return>", self.handle_input)
        self.entry.focus_set()

        self.send_btn = ttk.Button(input_frame, text="Send", command=lambda: self.handle_input(None))
        self.send_btn.pack(side=tk.LEFT)

    def log(self, message):
        self.terminal.configure(state=tk.NORMAL)
        self.terminal.insert(tk.END, message + "\n")
        self.terminal.see(tk.END)
        self.terminal.configure(state=tk.DISABLED)

    def start_client_process(self):
        try:
            self.process = subprocess.Popen(
                ["java", "-jar", "dist/client.jar"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1
            )
            threading.Thread(target=self.read_output, daemon=True).start()
        except Exception as e:
            self.log(f"Error starting client: {e}")

    def handle_input(self, event):
        cmd = self.entry.get()
        self.entry.delete(0, tk.END)
        if not cmd:
            return
        self.log(f"> {cmd}")
        if self.process and self.process.poll() is None:
            self.process.stdin.write(cmd + "\n")
            self.process.stdin.flush()
        else:
            self.log("Process is not running.")

    def read_output(self):
        while self.process and self.process.poll() is None:
            char = self.process.stdout.read(1)
            if not char:
                break
            self.after(0, self.append_text, char)
        self.after(0, self.log, "Client disconnected.")
        self.process = None

    def append_text(self, text):
        self.terminal.configure(state=tk.NORMAL)
        self.terminal.insert(tk.END, text)
        self.terminal.see(tk.END)
        self.terminal.configure(state=tk.DISABLED)

class ServerWindow(tk.Toplevel):
    def __init__(self, master):
        super().__init__(master)
        self.title("DFS Server Console")
        self.geometry("700x500")
        self.process = None
        self.setup_ui()
        self.master.master.app.center_window(self) if hasattr(self.master.master, 'app') else None
        self.protocol("WM_DELETE_WINDOW", self.stop_server)
        self.start_server_process()

    def setup_ui(self):
        self.log_area = scrolledtext.ScrolledText(self, state=tk.DISABLED, bg="#1e1e1e", fg="#ffffff", insertbackground="white", font=("Consolas", 10))
        self.log_area.pack(expand=True, fill=tk.BOTH, padx=5, pady=5)

        btn_frame = ttk.Frame(self, padding="5")
        btn_frame.pack(fill=tk.X)

        self.stop_btn = ttk.Button(btn_frame, text="Stop Server", command=self.stop_server)
        self.stop_btn.pack(side=tk.RIGHT)

    def log(self, message):
        self.log_area.configure(state=tk.NORMAL)
        self.log_area.insert(tk.END, message + "\n")
        self.log_area.see(tk.END)
        self.log_area.configure(state=tk.DISABLED)

    def start_server_process(self):
        try:
            self.process = subprocess.Popen(
                ["java", "-jar", "dist/server.jar"],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1
            )
            threading.Thread(target=self.read_output, daemon=True).start()
        except Exception as e:
            self.log(f"Error starting server: {e}")

    def read_output(self):
        while self.process and self.process.poll() is None:
            char = self.process.stdout.read(1)
            if not char:
                break
            self.after(0, self.append_text, char)
        self.after(0, self.log, "Server stopped.")
        self.process = None

    def append_text(self, text):
        self.log_area.configure(state=tk.NORMAL)
        self.log_area.insert(tk.END, text)
        self.log_area.see(tk.END)
        self.log_area.configure(state=tk.DISABLED)

    def stop_server(self):
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
        if self.winfo_exists():
            self.destroy()

class MainApp:
    def __init__(self, root):
        self.root = root
        self.root.title("DFS Management Control")
        self.root.geometry("450x250")
        self.center_window(self.root)

        self.server_window = None
        self.client_windows = []
        self.client_counter = 0

        self.setup_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def on_close(self):
        if self.server_window and self.server_window.winfo_exists():
            self.server_window.stop_server()
        for client in self.client_windows:
            if client.winfo_exists():
                client.stop_client()
        self.root.destroy()

    def center_window(self, window):
        window.update_idletasks()
        width = window.winfo_width()
        height = window.winfo_height()
        x = (window.winfo_screenwidth() // 2) - (width // 2)
        y = (window.winfo_screenheight() // 2) - (height // 2)
        window.geometry(f'{width}x{height}+{x}+{y}')

    def setup_ui(self):
        style = ttk.Style()
        style.configure("TButton", padding=6, font=("Segoe UI", 10))

        main_frame = ttk.Frame(self.root, padding="20")
        main_frame.pack(expand=True, fill=tk.BOTH)

        ttk.Label(main_frame, text="Distributed File System", font=("Segoe UI", 14, "bold")).pack(pady=10)

        btn_frame = ttk.Frame(main_frame)
        btn_frame.pack(pady=10)

        self.start_server_btn = ttk.Button(btn_frame, text="Launch Server", command=self.launch_server)
        self.start_server_btn.pack(side=tk.LEFT, padx=5)

        self.new_client_btn = ttk.Button(btn_frame, text="New Client", command=self.launch_client)
        self.new_client_btn.pack(side=tk.LEFT, padx=5)

        self.cleanup_btn = ttk.Button(btn_frame, text="Cleanup", command=self.run_cleanup)
        self.cleanup_btn.pack(side=tk.LEFT, padx=5)

    def run_cleanup(self):
        if messagebox.askyesno("Cleanup", "This will delete build artifacts and server data. Are you sure?"):
            try:
                subprocess.call([sys.executable, "cleanup.py", "-y"])
                messagebox.showinfo("Cleanup", "Cleanup completed successfully.")
            except Exception as e:
                messagebox.showerror("Error", f"Cleanup failed: {e}")

    def launch_server(self):
        if self.server_window is None or not self.server_window.winfo_exists():
            self.server_window = ServerWindow(self.root)
        else:
            self.server_window.lift()

    def launch_client(self):
        self.client_counter += 1
        client = ClientWindow(self.root, self.client_counter)
        self.client_windows.append(client)

if __name__ == "__main__":
    # Check if we should try to detach from the console on Windows
    if sys.platform == "win32" and "pythonw.exe" not in sys.executable.lower():
        if "--no-detach" not in sys.argv:
            import subprocess
            # Re-launch with pythonw.exe
            pythonw = sys.executable.replace("python.exe", "pythonw.exe")
            if os.path.exists(pythonw):
                subprocess.Popen([pythonw] + sys.argv + ["--no-detach"], creationflags=subprocess.DETACHED_PROCESS)
                sys.exit(0)

    if not os.path.exists("dist/server.jar") or not os.path.exists("dist/client.jar"):
        root = tk.Tk()
        root.withdraw()
        messagebox.showerror("Error", "Required JAR files not found in 'dist/'.\nPlease run installer.py first.")
        sys.exit(1)

    root = tk.Tk()
    app = MainApp(root)
    root.app = app # Make app accessible for centering
    root.mainloop()
