#!/usr/bin/env python3
import os
import sys
import json
import time
import uuid
import threading
import subprocess
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

# Global process registries
running_processes = {}
process_logs = {}
process_status = {}

# Root paths
MANTIS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIR = os.path.abspath(os.path.join(MANTIS_DIR, "..", "out", "mantis"))
STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")

class MantisWebRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        # Set directory for static files
        super().__init__(*args, directory=STATIC_DIR, **kwargs)

    def end_headers(self):
        # Allow CORS for easy development
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200, "ok")
        self.end_headers()

    def do_GET(self):
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        query = parse_qs(parsed_url.query)

        # API endpoints
        if path == "/api/files":
            self.handle_api_files()
        elif path == "/api/file-content":
            self.handle_api_file_content(query)
        elif path == "/api/stream":
            self.handle_api_stream(query)
        elif path == "/api/status":
            self.handle_api_status(query)
        else:
            # Serve static files via superclass SimpleHTTPRequestHandler
            super().do_GET()

    def do_POST(self):
        parsed_url = urlparse(self.path)
        path = parsed_url.path

        if path == "/api/run":
            self.handle_api_run()
        elif path == "/api/kill":
            self.handle_api_kill()
        else:
            self.send_error(404, "Not Found")

    # --- GET API Handlers ---

    def handle_api_files(self):
        """Scans the out/ folder recursively and returns a file system tree."""
        if not os.path.exists(OUT_DIR):
            os.makedirs(OUT_DIR, exist_ok=True)

        def get_tree(dir_path):
            tree = {
                "name": os.path.basename(dir_path),
                "type": "directory",
                "path": os.path.relpath(dir_path, OUT_DIR),
                "children": []
            }
            try:
                for item in sorted(os.listdir(dir_path)):
                    full_path = os.path.join(dir_path, item)
                    if os.path.isdir(full_path):
                        tree["children"].append(get_tree(full_path))
                    else:
                        tree["children"].append({
                            "name": item,
                            "type": "file",
                            "path": os.path.relpath(full_path, OUT_DIR),
                            "size": os.path.getsize(full_path)
                        })
            except Exception as e:
                print(f"Error scanning {dir_path}: {e}")
            return tree

        tree = get_tree(OUT_DIR)
        response_data = json.dumps(tree).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_data)))
        self.end_headers()
        self.wfile.write(response_data)

    def handle_api_file_content(self, query):
        """Reads the content of a file inside the out/ folder."""
        file_path_param = query.get("path", [None])[0]
        if not file_path_param:
            self.send_error(400, "Missing 'path' parameter")
            return

        # Basic directory traversal prevention
        clean_path = os.path.normpath(file_path_param).lstrip("/")
        full_path = os.path.join(OUT_DIR, clean_path)

        if not full_path.startswith(OUT_DIR):
            self.send_error(403, "Forbidden")
            return

        if not os.path.exists(full_path) or os.path.isdir(full_path):
            self.send_error(404, "File Not Found")
            return

        try:
            with open(full_path, "rb") as f:
                content = f.read()
            
            # Infer content type
            content_type = "text/plain"
            if full_path.endswith(".html"):
                content_type = "text/html"
            elif full_path.endswith(".md"):
                content_type = "text/markdown"
            elif full_path.endswith(".json"):
                content_type = "application/json"
            elif full_path.endswith(".yaml") or full_path.endswith(".yml"):
                content_type = "text/yaml"

            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        except Exception as e:
            self.send_error(500, f"Error reading file: {str(e)}")

    def handle_api_stream(self, query):
        """Streams live subprocess logs using Server-Sent Events (SSE)."""
        run_id = query.get("run_id", [None])[0]
        if not run_id:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing run_id")
            return

        if run_id not in process_logs:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Run not found")
            return

        # Set up SSE headers
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.end_headers()

        log_idx = 0
        try:
            while True:
                logs = process_logs.get(run_id, [])
                status = process_status.get(run_id, "running")

                # Write any new logs
                while log_idx < len(logs):
                    line = logs[log_idx]
                    # Escape event data newline
                    self.wfile.write(f"data: {json.dumps({'log': line})}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    log_idx += 1

                # End stream if process is done and all logs are consumed
                if status != "running" and log_idx >= len(logs):
                    self.wfile.write(f"data: {json.dumps({'status': status})}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    break

                time.sleep(0.1)
        except Exception as e:
            # Connection closed by client
            print(f"SSE connection closed for {run_id}: {e}")

    def handle_api_status(self, query):
        """Checks process status."""
        run_id = query.get("run_id", [None])[0]
        if not run_id:
            self.send_error(400, "Missing run_id")
            return

        status = process_status.get(run_id, "not_found")
        response_data = json.dumps({"run_id": run_id, "status": status}).encode("utf-8")
        
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_data)))
        self.end_headers()
        self.wfile.write(response_data)

    # --- POST API Handlers ---

    def handle_api_run(self):
        """Launches a Mantis CLI command in the background and logs output."""
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            params = json.loads(body) if body else {}

            tool = params.get("tool")  # "collect_stats", "evaluate_stability", "diagnose"
            options = params.get("options", [])  # List of arguments like ["--local", "--iterations", "3"]

            if not tool or tool not in ["collect_stats", "evaluate_stability", "diagnose"]:
                self.send_error(400, "Invalid tool specification")
                return

            # Formulate absolute command path
            executable = os.path.join(MANTIS_DIR, "bin", tool)
            if not os.path.exists(executable):
                self.send_error(404, f"Tool executable '{tool}' not found")
                return

            # Build command execution command array
            cmd = [executable] + options
            run_id = str(uuid.uuid4())

            process_logs[run_id] = []
            process_status[run_id] = "running"

            # Start execution thread
            thread = threading.Thread(target=self.execute_subprocess, args=(run_id, cmd))
            thread.daemon = True
            thread.start()

            response_data = json.dumps({
                "success": True,
                "run_id": run_id,
                "command": " ".join(cmd)
            }).encode("utf-8")

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response_data)))
            self.end_headers()
            self.wfile.write(response_data)

        except Exception as e:
            self.send_error(500, f"Failed to launch tool: {str(e)}")

    def handle_api_kill(self):
        """Kills a running process."""
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            params = json.loads(body) if body else {}

            run_id = params.get("run_id")
            if not run_id:
                self.send_error(400, "Missing run_id")
                return

            proc = running_processes.get(run_id)
            if proc:
                proc.terminate()
                process_status[run_id] = "terminated"
                response_data = json.dumps({"success": True, "message": "Process terminated"}).encode("utf-8")
            else:
                response_data = json.dumps({"success": False, "message": "Process not running or not found"}).encode("utf-8")

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response_data)))
            self.end_headers()
            self.wfile.write(response_data)

        except Exception as e:
            self.send_error(500, str(e))

    def execute_subprocess(self, run_id, cmd):
        """Executes a process and routes output lines to memory buffer."""
        print(f"Executing command: {' '.join(cmd)}")
        try:
            # We execute the command with the repository root (udmi) as workspace
            repo_root = os.path.abspath(os.path.join(MANTIS_DIR, ".."))
            
            # Ensure clean environment variables
            env = os.environ.copy()
            # If GEMINI_API_KEY or GITHUB_TOKEN is passed via workspace, subprocess inherits it automatically

            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                cwd=repo_root,
                env=env
            )
            running_processes[run_id] = proc

            while True:
                line = proc.stdout.readline()
                if not line:
                    break
                process_logs[run_id].append(line.rstrip())

            proc.wait()
            if process_status[run_id] == "running":
                if proc.returncode == 0:
                    process_status[run_id] = "completed"
                else:
                    process_status[run_id] = f"failed (code {proc.returncode})"
                    process_logs[run_id].append(f"\n[ERROR] Process exited with code {proc.returncode}")
        except Exception as e:
            process_status[run_id] = "failed"
            process_logs[run_id].append(f"\n[CRITICAL ERROR] Execution failed: {str(e)}")
        finally:
            if run_id in running_processes:
                del running_processes[run_id]

def run(port=8000):
    server_address = ("", port)
    httpd = HTTPServer(server_address, MantisWebRequestHandler)
    print(f"Mantis Web Control Center server running on http://localhost:{port}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer shutting down...")
        httpd.server_close()

if __name__ == "__main__":
    port = 8000
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            pass
    run(port)
