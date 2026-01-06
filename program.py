import subprocess
import os
import sys
import tkinter as tk
from tkinter import scrolledtext, ttk, messagebox
import threading

class ClientFrame(ttk.Frame):
    def __init__(self, master, client_id, on_close_callback):
        super().__init__(master)
        self.client_id = client_id
        self.on_close_callback = on_close_callback
        self.process = None
        self.setup_ui()
        self.start_client_process()

    def stop_client(self):
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
        self.on_close_callback(self)

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

        btn_frame = ttk.Frame(input_frame)
        btn_frame.pack(side=tk.LEFT)

        self.send_btn = ttk.Button(btn_frame, text="Send", command=lambda: self.handle_input(None))
        self.send_btn.pack(side=tk.LEFT)

        self.close_btn = ttk.Button(btn_frame, text="Close", command=self.stop_client)
        self.close_btn.pack(side=tk.LEFT, padx=5)

    def log(self, message):
        if not self.winfo_exists(): return
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
            if self.winfo_exists():
                self.after(0, self.append_text, char)
        if self.winfo_exists():
            self.after(0, self.log, "Client disconnected.")
        self.process = None

    def append_text(self, text):
        if not self.winfo_exists(): return
        self.terminal.configure(state=tk.NORMAL)
        self.terminal.insert(tk.END, text)
        self.terminal.see(tk.END)
        self.terminal.configure(state=tk.DISABLED)

class ServerFrame(ttk.Frame):
    def __init__(self, master, on_close_callback):
        super().__init__(master)
        self.on_close_callback = on_close_callback
        self.process = None
        self.setup_ui()
        self.start_server_process()

    def setup_ui(self):
        self.log_area = scrolledtext.ScrolledText(self, state=tk.DISABLED, bg="#1e1e1e", fg="#ffffff", insertbackground="white", font=("Consolas", 10))
        self.log_area.pack(expand=True, fill=tk.BOTH, padx=5, pady=5)

        btn_frame = ttk.Frame(self, padding="5")
        btn_frame.pack(fill=tk.X)

        self.stop_btn = ttk.Button(btn_frame, text="Stop Server & Close Tab", command=self.stop_server)
        self.stop_btn.pack(side=tk.RIGHT)

    def log(self, message):
        if not self.winfo_exists(): return
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
            if self.winfo_exists():
                self.after(0, self.append_text, char)
        if self.winfo_exists():
            self.after(0, self.log, "Server stopped.")
        self.process = None

    def append_text(self, text):
        if not self.winfo_exists(): return
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
        self.on_close_callback()

class MainApp:
    def __init__(self, root):
        self.root = root
        self.root.title("DFS Management Control")
        self.root.geometry("800x600")
        self.center_window(self.root)

        self.server_frame = None
        self.client_frames = {}
        self.client_counter = 0

        self.setup_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def on_close(self):
        if self.server_frame:
            self.server_frame.stop_server()
        for client in list(self.client_frames.values()):
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

        # Main horizontal paned window or just a vertical layout
        self.main_container = ttk.Frame(self.root)
        self.main_container.pack(expand=True, fill=tk.BOTH)

        # Top control bar
        control_frame = ttk.Frame(self.main_container, padding="10")
        control_frame.pack(side=tk.TOP, fill=tk.X)

        ttk.Label(control_frame, text="DFS Management", font=("Segoe UI", 12, "bold")).pack(side=tk.LEFT, padx=10)

        self.start_server_btn = ttk.Button(control_frame, text="Launch Server", command=self.launch_server)
        self.start_server_btn.pack(side=tk.LEFT, padx=5)

        self.new_client_btn = ttk.Button(control_frame, text="New Client", command=self.launch_client)
        self.new_client_btn.pack(side=tk.LEFT, padx=5)

        self.cleanup_btn = ttk.Button(control_frame, text="Cleanup", command=self.run_cleanup)
        self.cleanup_btn.pack(side=tk.LEFT, padx=5)

        # Notebook for sub-pages
        self.notebook = ttk.Notebook(self.main_container)
        self.notebook.pack(expand=True, fill=tk.BOTH, padx=5, pady=5)

    def run_cleanup(self):
        if messagebox.askyesno("Cleanup", "This will delete build artifacts and server data. Are you sure?"):
            try:
                subprocess.call([sys.executable, "cleanup.py", "-y"])
                messagebox.showinfo("Cleanup", "Cleanup completed successfully.")
            except Exception as e:
                messagebox.showerror("Error", f"Cleanup failed: {e}")

    def launch_server(self):
        if self.server_frame is None:
            self.server_frame = ServerFrame(self.notebook, self.on_server_close)
            self.notebook.add(self.server_frame, text="Server Console")
        self.notebook.select(self.server_frame)

    def on_server_close(self):
        if self.server_frame:
            self.notebook.forget(self.server_frame)
            self.server_frame.destroy()
            self.server_frame = None

    def launch_client(self):
        self.client_counter += 1
        client_id = self.client_counter
        client_frame = ClientFrame(self.notebook, client_id, self.on_client_close)
        self.client_frames[client_id] = client_frame
        self.notebook.add(client_frame, text=f"Client {client_id}")
        self.notebook.select(client_frame)

    def on_client_close(self, client_frame):
        client_id = client_frame.client_id
        if client_id in self.client_frames:
            self.notebook.forget(client_frame)
            client_frame.destroy()
            del self.client_frames[client_id]

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
