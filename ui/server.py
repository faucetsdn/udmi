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

# Global process handles
sequencer_process = None
triage_process = None

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
        elif parsed_url.path == '/api/run_triage':
            self.handle_run_triage(parsed_url.query)
        elif parsed_url.path == '/api/stop_triage':
            self.handle_stop_triage()
        elif parsed_url.path == '/api/triage_status':
            self.handle_triage_status(parsed_url.query)
        elif parsed_url.path == '/api/triage_report':
            self.handle_triage_report(parsed_url.query)
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

    def handle_run_triage(self, query_string):
        global triage_process
        params = urllib.parse.parse_qs(query_string)
        device_id = params.get('device_id', [None])[0]
        test_id = params.get('test_id', [None])[0]
        playbook = params.get('playbook', [None])[0]
        project_spec = params.get('project_spec', [None])[0]
        site_model = params.get('site_model', [None])[0]

        if not device_id or not test_id:
            self.send_error_response(400, "Missing required parameters: device_id, test_id")
            return

        # 1. Resolve virtualenv python binary
        python_bin = os.path.join(ROOT_DIR, 'venv', 'bin', 'python3')
        if not os.path.exists(python_bin):
            python_bin = sys.executable # Fallback to current if virtualenv is missing

        # 2. Setup out/ directory and verify file existence for the manifest
        out_dir = os.path.join(ROOT_DIR, 'out')
        os.makedirs(out_dir, exist_ok=True)

        site_id = os.path.basename(os.path.normpath(site_model)) if site_model else "udmi_site_model"
        site_model_abs = os.path.abspath(os.path.expanduser(site_model)) if site_model else os.path.join(ROOT_DIR, "sites", "udmi_site_model")
        
        # 1. Resolve absolute paths for the logs directly from the site model directory
        seq_log_abs = os.path.join(site_model_abs, "out", "devices", device_id, "tests", test_id, "sequence.log")
        seq_md_abs = os.path.join(site_model_abs, "out", "devices", device_id, "tests", test_id, "sequence.md")
        pubber_log_abs = os.path.join(ROOT_DIR, "out", "pubber.log")
        udmis_log_abs = os.path.join(ROOT_DIR, "out", "udmis.log")

        # Strict Guardrail: If sequence_log is absent, do not trigger the run at all!
        if not os.path.exists(seq_log_abs):
            self.send_error_response(412, f"Triage aborted: Sequencer log not found for '{test_id}'. Please run the compliance test case first to generate logs.")
            return

        # Smart Guardrail: If the test case passed successfully, do not run triage!
        try:
            with open(seq_log_abs, 'r', encoding='utf-8', errors='ignore') as f:
                log_content = f.read()
                if "RESULT pass" in log_content:
                    response_data = json.dumps({
                        "status": "Skipped",
                        "message": f"Compliance test case '{test_id}' passed successfully. Diagnostics are not required."
                    }).encode('utf-8')
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Content-Length', str(len(response_data)))
                    self.send_header('Access-Control-Allow-Origin', '*')
                    self.end_headers()
                    self.wfile.write(response_data)
                    return
        except Exception as e:
            print(f"Warning: Failed to check sequence.log for passing status: {e}")

        # 2. Convert all absolute paths to relative paths relative to ROOT_DIR (UDMI root)
        # This ensures they integrate perfectly with Mantis's internal path joining (handling custom sites directories seamlessly)
        seq_log_rel = os.path.relpath(seq_log_abs, ROOT_DIR)
        seq_md_rel = os.path.relpath(seq_md_abs, ROOT_DIR)
        pubber_log_rel = os.path.relpath(pubber_log_abs, ROOT_DIR)
        udmis_log_rel = os.path.relpath(udmis_log_abs, ROOT_DIR)

        # Dynamically compile the logs dictionary, including only files that exist on disk
        failed_run_logs = {
            "sequence_log": seq_log_rel
        }

        if os.path.exists(seq_md_abs):
            failed_run_logs["sequence_md"] = seq_md_rel
        if os.path.exists(pubber_log_abs):
            failed_run_logs["pubber_log"] = pubber_log_rel
        if os.path.exists(udmis_log_abs):
            failed_run_logs["udmis_log"] = udmis_log_rel

        manifest_data = {
            "metadata": {
                "target_project": project_spec or "//mqtt/localhost",
                "site_id": site_id
            },
            "failures": [
                {
                    "test_name": test_id,
                    "device_id": device_id,
                    "suite": "both",
                    "category": "unknown",
                    "run_directory": "out",
                    "logs": {
                        "failed_run": failed_run_logs
                    }
                }
            ]
        }

        manifest_path = os.path.join(out_dir, 'triage_manifest.json')
        try:
            with open(manifest_path, 'w', encoding='utf-8') as fm:
                json.dump(manifest_data, fm, indent=2)
        except Exception as e:
            print(f"Warning: failed to write triage_manifest.json: {e}")

        # 3. Build the Mantis triage command in Manifest Mode:
        # python3 -u -m mantis.src.app.main -m out/triage_manifest.json -d DEVICE_ID -t TEST_ID [--playbook PLAYBOOK_PATH]
        manifest_relative_path = os.path.join('out', 'triage_manifest.json')
        cmd = [python_bin, "-u", "-m", "mantis.src.app.main", "-m", manifest_relative_path, "-d", device_id, "-t", test_id]

        if playbook == "swe":
            playbook_path = os.path.join(ROOT_DIR, 'util', 'mantis', 'config', 'playbook_swe.yaml')
            cmd.extend(["--playbook", playbook_path])

        # 4. Setup PYTHONPATH so it can resolve tools, util/ (for mantis namespace), and util/mantis/src
        env = os.environ.copy()
        mantis_src = os.path.join(ROOT_DIR, 'util', 'mantis', 'src')
        util_dir = os.path.join(ROOT_DIR, 'util')
        tools_dir = os.path.join(ROOT_DIR, 'tools')
        env['PYTHONPATH'] = f"{tools_dir}:{mantis_src}:{util_dir}:{env.get('PYTHONPATH', '')}"

        log_path = os.path.join(out_dir, 'triage.log')
        if os.path.exists(log_path):
            try:
                os.remove(log_path)
            except:
                pass

        try:
            # Spawn the process in a new process group so we can kill its children
            triage_process = subprocess.Popen(
                cmd,
                cwd=ROOT_DIR,
                env=env,
                stdout=open(log_path, 'wb', buffering=0),
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            
            response_data = json.dumps({
                "status": "Started",
                "pid": triage_process.pid,
                "cmd": cmd
            }).encode('utf-8')
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(response_data)
        except Exception as e:
            self.send_error_response(500, f"Failed to start Mantis triage: {str(e)}")

    def handle_stop_triage(self):
        global triage_process
        if triage_process is None or triage_process.poll() is not None:
            response_data = json.dumps({"status": "Not running"}).encode('utf-8')
        else:
            try:
                pgid = os.getpgid(triage_process.pid)
                os.killpg(pgid, signal.SIGTERM)
                triage_process.wait(timeout=2)
                triage_process = None
                response_data = json.dumps({"status": "Stopped"}).encode('utf-8')
            except Exception as e:
                try:
                    triage_process.kill()
                    triage_process = None
                    response_data = json.dumps({"status": "Stopped (fallback)", "error": str(e)}).encode('utf-8')
                except Exception as ex:
                    self.send_error_response(500, f"Failed to stop triage process: {str(ex)}")
                    return

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

    def handle_triage_status(self, query_string):
        global triage_process
        params = urllib.parse.parse_qs(query_string)
        offset_param = params.get('offset', [0])[0]
        
        try:
            offset = int(offset_param)
        except:
            offset = 0

        is_running = triage_process is not None and triage_process.poll() is None
        exit_code = triage_process.poll() if triage_process else None

        log_content = ""
        new_offset = offset
        log_path = os.path.join(ROOT_DIR, 'out', 'triage.log')

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

    def handle_triage_report(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', [None])[0]
        project_spec = params.get('project_spec', [None])[0]
        device_id = params.get('device_id', [None])[0]
        test_id = params.get('test_id', [None])[0]

        if not all([site_model, project_spec, device_id, test_id]):
            self.send_error_response(400, "Missing required parameters: site_model, project_spec, device_id, test_id")
            return

        # Resolve path dynamically using target project spec and site model
        clean_target = project_spec.replace("/", "_").replace("+", "_").strip("_")
        site_model_resolved = os.path.expanduser(site_model)
        site_id = os.path.basename(os.path.normpath(site_model_resolved))
        report_path = os.path.join(ROOT_DIR, 'out', 'diagnose', clean_target, site_id, device_id, test_id, 'triage_analysis.md')

        if not os.path.exists(report_path):
            self.send_error_response(404, f"Diagnostic report not found for test '{test_id}'. It may still be running.")
            return

        try:
            with open(report_path, 'r', encoding='utf-8') as f:
                report_content = f.read()
            
            response_data = report_content.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/markdown; charset=utf-8')
            self.send_header('Content-Length', str(len(response_data)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(response_data)
        except Exception as e:
            self.send_error_response(500, f"Error reading diagnostic report: {str(e)}")

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
