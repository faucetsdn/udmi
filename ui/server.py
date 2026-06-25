import os
import sys
import json
import urllib.parse
import subprocess
import signal
from http.server import SimpleHTTPRequestHandler, HTTPServer

PORT = 8080
# Set the root directory to the parent of 'ui' (the UDMI repo root)
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

# Global registry of active server-enforced features (default to both)
ALLOWED_FEATURES = {'sequencer', 'mantis'}

# Global process handle
sequencer_process = None

class UDMIRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT_DIR, **kwargs)

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        
        # Strict Path Guard: Block access to disabled feature folders (e.g. /ui/mantis/)
        path_parts = parsed_url.path.strip('/').split('/')
        if len(path_parts) >= 2 and path_parts[0] == 'ui':
            feature_name = path_parts[1]
            if feature_name in {'sequencer', 'mantis'} and feature_name not in ALLOWED_FEATURES:
                self.send_response(403)
                self.send_header('Content-Type', 'text/plain')
                self.end_headers()
                self.wfile.write(f"Forbidden: The '{feature_name}' tool is disabled on this server.".encode('utf-8'))
                return

        if parsed_url.path == '/api/features':
            self.handle_api_features()
        elif parsed_url.path == '/api/list':
            self.handle_api_list(parsed_url.query)
        elif parsed_url.path == '/api/read_file':
            self.handle_api_read_file(parsed_url.query)
        elif parsed_url.path == '/api/run_sequencer':
            self.handle_run_sequencer(parsed_url.query)
        elif parsed_url.path == '/api/stop_sequencer':
            self.handle_stop_sequencer()
        elif parsed_url.path == '/api/sequencer_status':
            self.handle_sequencer_status(parsed_url.query)
        else:
            # Regular static file serving
            super().do_GET()

    def handle_api_list(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        path_param = params.get('path', [None])[0]
        
        if not path_param:
            self.send_error_response(400, "Missing 'path' parameter")
            return

        # Resolve path (can be absolute, home-relative, or relative to repo root)
        target_path = os.path.expanduser(path_param)
        if not os.path.isabs(target_path):
            target_path = os.path.abspath(os.path.join(ROOT_DIR, target_path))

        if not os.path.exists(target_path):
            self.send_error_response(404, f"Path not found: {target_path}")
            return

        try:
            # List directories and files inside target_path
            subdirs = []
            files = []
            for name in os.listdir(target_path):
                full_path = os.path.join(target_path, name)
                if os.path.isdir(full_path):
                    subdirs.append(name)
                elif os.path.isfile(full_path):
                    files.append(name)
            subdirs.sort()
            files.sort()
            
            # Send JSON response containing resolved absolute path, folders, and files
            response_data = json.dumps({
                "path": target_path,
                "folders": subdirs,
                "files": files
            }).encode('utf-8')

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(response_data)
        except Exception as e:
            self.send_error_response(500, str(e))

    def handle_api_read_file(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        path_param = params.get('path', [None])[0]
        
        if not path_param:
            self.send_error_response(400, "Missing 'path' parameter")
            return

        # Resolve path (can be absolute, home-relative, or relative to repo root)
        target_path = os.path.expanduser(path_param)
        if not os.path.isabs(target_path):
            target_path = os.path.abspath(os.path.join(ROOT_DIR, target_path))

        if not os.path.exists(target_path) or not os.path.isfile(target_path):
            self.send_error_response(404, f"File not found: {target_path}")
            return

        try:
            with open(target_path, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()
            
            response_data = content.encode('utf-8')

            self.send_response(200)
            self.send_header('Content-Type', 'text/plain; charset=utf-8')
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(response_data)
        except Exception as e:
            self.send_error_response(500, str(e))

    def handle_run_sequencer(self, query_string):
        global sequencer_process
        params = urllib.parse.parse_qs(query_string)
        
        # Check if already running
        if sequencer_process is not None and sequencer_process.poll() is None:
            self.send_error_response(400, "Sequencer is already running.")
            return

        site_model = params.get('site_model', [None])[0]
        project_spec = params.get('project_spec', [None])[0]
        device_id = params.get('device_id', [None])[0]
        tests_param = params.get('tests', [None])[0]
        
        log_level = params.get('log_level', ['INFO'])[0]
        min_stage = params.get('min_stage', ['PREVIEW'])[0]
        serial_no = params.get('serial_no', [None])[0]

        if not site_model or not project_spec or not device_id:
            self.send_error_response(400, "Missing required parameters: site_model, project_spec, device_id")
            return

        # Resolve site model path
        site_model_resolved = os.path.expanduser(site_model)
        if not os.path.isabs(site_model_resolved):
            site_model_resolved = os.path.abspath(os.path.join(ROOT_DIR, site_model_resolved))

        # Build command array:
        # bin/sequencer [-v] [-vv] [-a] [-x] [-s serial_no] SITE_MODEL PROJECT_SPEC DEVICE_ID [TEST_NAMES...]
        cmd = ["bin/sequencer"]
        
        if log_level == "DEBUG":
            cmd.append("-v")
        elif log_level == "TRACE":
            cmd.append("-vv")
            
        if min_stage == "ALPHA":
            cmd.append("-a")
        elif min_stage == "ALPHA_ONLY":
            cmd.append("-x")
            
        if serial_no and serial_no.strip():
            cmd.append("-s")
            cmd.append(serial_no.strip())

        cmd.append(site_model_resolved)
        cmd.append(project_spec)
        cmd.append(device_id)

        if tests_param:
            test_names = [t.strip() for t in tests_param.split(',') if t.strip()]
            cmd.extend(test_names)

        # Clear/Create out/ directory if it doesn't exist
        out_dir = os.path.join(ROOT_DIR, 'out')
        os.makedirs(out_dir, exist_ok=True)
        
        log_path = os.path.join(out_dir, 'sequencer.log')
        if os.path.exists(log_path):
            try:
                os.remove(log_path)
            except:
                pass

        try:
            # Spawn the process in a new process group so we can kill its children
            sequencer_process = subprocess.Popen(
                cmd,
                cwd=ROOT_DIR,
                stdout=open(log_path, 'wb', buffering=0),
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            
            response_data = json.dumps({
                "status": "Started",
                "pid": sequencer_process.pid,
                "cmd": cmd
            }).encode('utf-8')
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(response_data)
        except Exception as e:
            self.send_error_response(500, f"Failed to start sequencer: {str(e)}")

    def handle_stop_sequencer(self):
        global sequencer_process
        if sequencer_process is None or sequencer_process.poll() is not None:
            response_data = json.dumps({"status": "Not running"}).encode('utf-8')
        else:
            try:
                # Kill the entire process group
                pgid = os.getpgid(sequencer_process.pid)
                os.killpg(pgid, signal.SIGTERM)
                sequencer_process.wait(timeout=2)
                sequencer_process = None
                response_data = json.dumps({"status": "Stopped"}).encode('utf-8')
            except Exception as e:
                # Fallback to direct kill if setsid failed
                try:
                    sequencer_process.kill()
                    sequencer_process = None
                    response_data = json.dumps({"status": "Stopped (fallback)", "error": str(e)}).encode('utf-8')
                except Exception as ex:
                    self.send_error_response(500, f"Failed to stop process: {str(ex)}")
                    return

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

    def handle_sequencer_status(self, query_string):
        global sequencer_process
        params = urllib.parse.parse_qs(query_string)
        offset_param = params.get('offset', [0])[0]
        
        try:
            offset = int(offset_param)
        except:
            offset = 0

        is_running = sequencer_process is not None and sequencer_process.poll() is None
        exit_code = sequencer_process.poll() if sequencer_process else None

        log_content = ""
        new_offset = offset
        log_path = os.path.join(ROOT_DIR, 'out', 'sequencer.log')

        if os.path.exists(log_path):
            try:
                with open(log_path, 'r') as f:
                    f.seek(0, os.SEEK_END)
                    file_size = f.tell()
                    
                    if offset < file_size:
                        f.seek(offset)
                        log_content = f.read()
                        new_offset = f.tell()
            except Exception as e:
                log_content = f"[Server Error reading log: {str(e)}]\n"

        response_data = json.dumps({
            "running": is_running,
            "exit_code": exit_code,
            "log": log_content,
            "offset": new_offset
        }).encode('utf-8')

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

    def send_error_response(self, code, message):
        response_data = json.dumps({"error": message}).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.end_headers()
        self.wfile.write(response_data)

    def handle_api_features(self):
        response_data = json.dumps(list(ALLOWED_FEATURES)).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

if __name__ == '__main__':
    # Parse command line arguments for enabled features
    for arg in sys.argv:
        if arg.startswith('--features='):
            features_str = arg.split('=', 1)[1].lower()
            ALLOWED_FEATURES = set(f.strip() for f in features_str.split(',') if f.strip())

    print(f"Starting UDMI custom API & Static server on port {PORT} serving directory {ROOT_DIR}")
    print(f"Enforced server-side features: {list(ALLOWED_FEATURES)}")
    
    try:
        server = HTTPServer(('0.0.0.0', PORT), UDMIRequestHandler)
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
        sys.exit(0)
