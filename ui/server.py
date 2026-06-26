import os
import sys
import json
import urllib.parse
import subprocess
import signal
import uuid
import threading
import shutil
from datetime import datetime
from http.server import SimpleHTTPRequestHandler, HTTPServer

PORT = 8080
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
HOME_DIR = os.path.abspath(os.path.expanduser('~'))


def to_home_relative(path_str):
    if not path_str:
        return path_str
    abs_path = os.path.abspath(os.path.expanduser(path_str))
    if abs_path == HOME_DIR:
        return "~"
    if abs_path.startswith(HOME_DIR + os.sep):
        return "~" + abs_path[len(HOME_DIR):]
    return abs_path


ALLOWED_FEATURES = {'sequencer', 'mantis'}


active_processes_lock = threading.Lock()
active_processes = {}


def get_latest_session_process(proc_type: str):
    with active_processes_lock:
        matching = [
            (sid, meta) for sid, meta in active_processes.items()
            if meta.get('type') == proc_type
        ]
        if not matching:
            return None, None
        matching.sort(key=lambda x: x[1].get('created_at', ''), reverse=True)
        return matching[0]


def prune_old_sessions(max_sessions=10):
    sessions_dir = os.path.join(ROOT_DIR, 'out', 'sessions')
    if not os.path.exists(sessions_dir) or not os.path.isdir(sessions_dir):
        return
    try:
        entries = []
        for entry in os.listdir(sessions_dir):
            full_path = os.path.join(sessions_dir, entry)
            if os.path.isdir(full_path):
                mtime = os.path.getmtime(full_path)
                entries.append((full_path, mtime))
        
        entries.sort(key=lambda x: x[1], reverse=True)
        
        with active_processes_lock:
            active_sids = set(active_processes.keys())
        
        kept = 0
        for full_path, mtime in entries:
            sid = os.path.basename(full_path)
            if sid in active_sids:
                continue
            kept += 1
            if kept > max_sessions:
                shutil.rmtree(full_path, ignore_errors=True)
    except Exception as e:
        print(f"Warning: Failed to prune old sessions: {e}")


class UDMIRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT_DIR, **kwargs)

    def do_POST(self):
        parsed_url = urllib.parse.urlparse(self.path)
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = {}
        if content_length > 0:
            body = self.rfile.read(content_length).decode('utf-8')
            try:
                post_data = json.loads(body)
            except Exception:
                post_data = dict(urllib.parse.parse_qsl(body))

        auth_header = self.headers.get('Authorization', '')
        bearer_key = None
        if auth_header.startswith('Bearer '):
            bearer_key = auth_header[7:].strip()

        if parsed_url.path == '/api/run_triage':
            self.handle_run_triage(parsed_url.query, post_data=post_data, bearer_key=bearer_key)
        elif parsed_url.path == '/api/stop_triage':
            self.handle_stop_triage(parsed_url.query, post_data=post_data)
        elif parsed_url.path == '/api/run_sequencer':
            self.handle_run_sequencer(parsed_url.query, post_data=post_data)
        elif parsed_url.path == '/api/stop_sequencer':
            self.handle_stop_sequencer(parsed_url.query, post_data=post_data)
        else:
            self.send_error_response(404, "Endpoint not found")

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        
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
        elif parsed_url.path == '/api/device_results':
            self.handle_device_results(parsed_url.query)
        elif parsed_url.path == '/api/run_sequencer':
            self.handle_run_sequencer(parsed_url.query)
        elif parsed_url.path == '/api/stop_sequencer':
            self.handle_stop_sequencer(parsed_url.query)
        elif parsed_url.path == '/api/sequencer_status':
            self.handle_sequencer_status(parsed_url.query)
        elif parsed_url.path == '/api/run_triage':
            self.handle_run_triage(parsed_url.query)
        elif parsed_url.path == '/api/stop_triage':
            self.handle_stop_triage(parsed_url.query)
        elif parsed_url.path == '/api/triage_status':
            self.handle_triage_status(parsed_url.query)
        elif parsed_url.path == '/api/triage_report':
            self.handle_triage_report(parsed_url.query)
        else:
            super().do_GET()

    def _resolve_and_verify_path(self, path_param):
        if not path_param:
            path_param = '~'
        
        target_path = os.path.expanduser(path_param)
        if not os.path.isabs(target_path):
            target_path = os.path.join(ROOT_DIR, target_path)
        target_path = os.path.abspath(target_path)

        try:
            if os.path.commonpath([HOME_DIR, target_path]) != HOME_DIR:
                return None, (403, "Access denied: Path outside home directory")
        except Exception:
            return None, (403, "Access denied: Invalid path")

        return target_path, None

    def handle_api_list(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        path_param = params.get('path', [None])[0]
        
        target_path, err = self._resolve_and_verify_path(path_param)
        if err:
            self.send_error_response(err[0], err[1])
            return

        if not os.path.exists(target_path):
            self.send_error_response(404, f"Path not found: {target_path}")
            return

        try:
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
            
            response_data = json.dumps({
                "path": to_home_relative(target_path),
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
        
        target_path, err = self._resolve_and_verify_path(path_param)
        if err:
            self.send_error_response(err[0], err[1])
            return

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


    def handle_device_results(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', [None])[0]
        device = params.get('device', [None])[0]

        if not site_model or not device:
            self.send_error_response(400, "Missing 'site_model' or 'device' parameter")
            return

        target_dir = os.path.expanduser(site_model)
        if not os.path.isabs(target_dir):
            target_dir = os.path.abspath(os.path.join(ROOT_DIR, target_dir))

        tests_dir = os.path.join(target_dir, 'out', 'devices', device, 'tests')

        results = {}
        if os.path.exists(tests_dir) and os.path.isdir(tests_dir):
            try:
                for test_name in os.listdir(tests_dir):
                    test_path = os.path.join(tests_dir, test_name)
                    if os.path.isdir(test_path):
                        seq_log = os.path.join(test_path, 'sequence.log')
                        seq_md = os.path.join(test_path, 'sequence.md')
                        
                        mtime = os.path.getmtime(test_path)
                        if os.path.exists(seq_log):
                            mtime = max(mtime, os.path.getmtime(seq_log))
                        
                        formatted_ts = datetime.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M:%S')

                        status = 'idle'
                        if os.path.exists(seq_md):
                            with open(seq_md, 'r', encoding='utf-8', errors='replace') as f:
                                md_content = f.read()
                            md_lower = md_content.lower()
                            if 'test passed' in md_lower or 'sequence complete' in md_lower:
                                status = 'pass'
                            elif 'test skipped' in md_lower:
                                status = 'skip'
                            elif os.path.exists(seq_log):
                                status = 'fail'
                        elif os.path.exists(seq_log):
                            status = 'fail'

                        project_spec = None
                        # Scan .attr files in test directory as authoritative source for project ID
                        try:
                            attr_files = [f for f in os.listdir(test_path) if f.endswith('.attr')]
                            attr_files.sort(key=lambda x: (0 if 'validation' in x else (1 if 'system' in x else 2)))
                            for attr_file in attr_files:
                                try:
                                    with open(os.path.join(test_path, attr_file), 'r', encoding='utf-8') as af:
                                        a_data = json.load(af)
                                        p_id = a_data.get('projectId')
                                        if p_id:
                                            project_spec = p_id
                                            break
                                except Exception:
                                    pass
                        except Exception:
                            pass

                        if not project_spec:
                            run_info_file = os.path.join(test_path, 'run_info.json')
                            if os.path.exists(run_info_file):
                                try:
                                    with open(run_info_file, 'r', encoding='utf-8') as rf:
                                        r_data = json.load(rf)
                                        project_spec = r_data.get('project_spec') or r_data.get('target_project')
                                except Exception:
                                    pass

                        if not project_spec:
                            sessions_dir = os.path.join(ROOT_DIR, 'out', 'sessions')
                            if os.path.exists(sessions_dir):
                                for s in sorted(os.listdir(sessions_dir), reverse=True):
                                    m_path = os.path.join(sessions_dir, s, 'triage_manifest.json')
                                    if os.path.exists(m_path):
                                        try:
                                            with open(m_path, 'r', encoding='utf-8') as mf:
                                                m_data = json.load(mf)
                                                items = m_data.get('failures', []) + m_data.get('successes', [])
                                                for f_item in items:
                                                    if f_item.get('device_id') == device and f_item.get('test_name') == test_name:
                                                        project_spec = m_data.get('metadata', {}).get('target_project')
                                                        break
                                        except Exception:
                                            pass
                                    if project_spec:
                                        break

                        results[test_name] = {
                            "status": status,
                            "timestamp": formatted_ts,
                            "project_spec": project_spec
                        }
            except Exception as e:
                self.send_error_response(500, f"Error scanning test directory: {str(e)}")
                return

        response_data = json.dumps({
            "device": device,
            "results": results
        }).encode('utf-8')

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

    def handle_run_sequencer(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        
        site_model = data.get('site_model') or params.get('site_model', [None])[0]
        project_spec = data.get('project_spec') or params.get('project_spec', [None])[0]
        device_id = data.get('device_id') or params.get('device_id', [None])[0]
        tests_param = data.get('tests') or params.get('tests', [None])[0]
        
        log_level = data.get('log_level') or params.get('log_level', ['INFO'])[0]
        min_stage = data.get('min_stage') or params.get('min_stage', ['PREVIEW'])[0]
        serial_no = data.get('serial_no') or params.get('serial_no', [None])[0]

        if not site_model or not project_spec or not device_id:
            self.send_error_response(400, "Missing required parameters: site_model, project_spec, device_id")
            return

        session_id = str(uuid.uuid4())
        session_dir = os.path.join(ROOT_DIR, 'out', 'sessions', session_id)
        os.makedirs(session_dir, exist_ok=True)
        prune_old_sessions(10)

        site_model_resolved = os.path.expanduser(site_model)
        if not os.path.isabs(site_model_resolved):
            site_model_resolved = os.path.abspath(os.path.join(ROOT_DIR, site_model_resolved))

        cmd = ["bin/sequencer"]
        if log_level == "DEBUG":
            cmd.append("-v")
        elif log_level == "TRACE":
            cmd.append("-vv")
            
        if min_stage == "ALPHA":
            cmd.append("-a")
        elif min_stage == "ALPHA_ONLY":
            cmd.append("-x")
            
        if serial_no and str(serial_no).strip():
            cmd.append("-s")
            cmd.append(str(serial_no).strip())

        cmd.append(site_model_resolved)
        cmd.append(project_spec)
        cmd.append(device_id)

        if tests_param:
            if isinstance(tests_param, list):
                cmd.extend(tests_param)
            else:
                test_names = [t.strip() for t in str(tests_param).split(',') if t.strip()]
                cmd.extend(test_names)

        log_path = os.path.join(session_dir, 'sequencer.log')

        # Save run metadata for historical target project tracking
        session_meta_file = os.path.join(session_dir, 'run_info.json')
        try:
            with open(session_meta_file, 'w', encoding='utf-8') as smf:
                json.dump({"project_spec": project_spec, "device_id": device_id, "timestamp": datetime.now().isoformat()}, smf, indent=2)
        except Exception:
            pass

        if tests_param:
            test_list = tests_param if isinstance(tests_param, list) else [t.strip() for t in str(tests_param).split(',') if t.strip()]
            for t_name in test_list:
                t_dir = os.path.join(site_model_resolved, 'out', 'devices', device_id, 'tests', t_name)
                os.makedirs(t_dir, exist_ok=True)
                try:
                    with open(os.path.join(t_dir, 'run_info.json'), 'w', encoding='utf-8') as rf:
                        json.dump({"project_spec": project_spec, "device_id": device_id, "timestamp": datetime.now().isoformat()}, rf, indent=2)
                except Exception:
                    pass

        try:
            proc = subprocess.Popen(
                cmd,
                cwd=ROOT_DIR,
                stdout=open(log_path, 'wb', buffering=0),
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            
            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "sequencer",
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            response_data = json.dumps({
                "status": "Started",
                "session_id": session_id,
                "pid": proc.pid,
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

    def handle_stop_sequencer(self, query_string=None, post_data=None):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        data = post_data or {}
        session_id = data.get('session_id') or params.get('session_id', [None])[0]

        target_meta = None
        target_sid = None
        with active_processes_lock:
            if session_id and session_id in active_processes:
                target_sid = session_id
                target_meta = active_processes[session_id]
            else:
                target_sid, target_meta = get_latest_session_process('sequencer')

        if not target_meta or not target_meta.get('process') or target_meta['process'].poll() is not None:
            response_data = json.dumps({"status": "Not running"}).encode('utf-8')
        else:
            proc = target_meta['process']
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
                proc.wait(timeout=2)
                response_data = json.dumps({"status": "Stopped", "session_id": target_sid}).encode('utf-8')
            except Exception as e:
                try:
                    proc.kill()
                    response_data = json.dumps({"status": "Stopped (fallback)", "session_id": target_sid, "error": str(e)}).encode('utf-8')
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
        params = urllib.parse.parse_qs(query_string)
        session_id = params.get('session_id', [None])[0]
        offset_param = params.get('offset', [0])[0]
        
        try:
            offset = int(offset_param)
        except Exception:
            offset = 0

        target_meta = None
        target_sid = session_id
        if target_sid:
            with active_processes_lock:
                target_meta = active_processes.get(target_sid)
        else:
            target_sid, target_meta = get_latest_session_process('sequencer')

        proc = target_meta.get('process') if target_meta else None
        is_running = proc is not None and proc.poll() is None
        exit_code = proc.poll() if proc else None

        log_content = ""
        new_offset = offset
        log_path = target_meta.get('log_path') if target_meta else (os.path.join(ROOT_DIR, 'out', 'sessions', target_sid, 'sequencer.log') if target_sid else os.path.join(ROOT_DIR, 'out', 'sequencer.log'))

        if log_path and os.path.exists(log_path):
            try:
                with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
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
            "session_id": target_sid,
            "log": log_content,
            "offset": new_offset
        }).encode('utf-8')

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response_data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_data)

    def handle_run_triage(self, query_string, post_data=None, bearer_key=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        
        device_id = data.get('device_id') or params.get('device_id', [None])[0]
        test_id = data.get('test_id') or params.get('test_id', [None])[0]
        playbook = data.get('playbook') or params.get('playbook', [None])[0]
        project_spec = data.get('project_spec') or params.get('project_spec', [None])[0]
        site_model = data.get('site_model') or params.get('site_model', [None])[0]

        gemini_key = bearer_key or data.get('gemini_api_key') or data.get('api_key') or os.getenv("GEMINI_API_KEY")
        use_vertex_param = data.get('use_vertex') or params.get('use_vertex', [None])[0]
        gcp_project_param = data.get('gcp_project') or params.get('gcp_project', [None])[0]
        gcp_location_param = data.get('gcp_location') or params.get('gcp_location', [None])[0]
        fetch_udmis_param = data.get('fetch_udmis') or params.get('fetch_udmis', [None])[0]
        cloud_project_param = data.get('cloud_project') or params.get('cloud_project', [None])[0] or gcp_project_param

        if not device_id or not test_id:
            self.send_error_response(400, "Missing required parameters: device_id, test_id")
            return

        use_vertex = (str(use_vertex_param).lower() == 'true') if use_vertex_param else (os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes"))
        if not gemini_key and not use_vertex:
            self.send_error_response(412, "Triage aborted: GEMINI_API_KEY is not configured. Please enter your Gemini API Key in Diagnostic Settings or set the environment variable.")
            return

        session_id = str(uuid.uuid4())
        session_dir = os.path.join(ROOT_DIR, 'out', 'sessions', session_id)
        os.makedirs(session_dir, exist_ok=True)
        prune_old_sessions(10)

        python_bin = os.path.join(ROOT_DIR, 'venv', 'bin', 'python3')
        if not os.path.exists(python_bin):
            python_bin = sys.executable

        site_id = os.path.basename(os.path.normpath(site_model)) if site_model else "udmi_site_model"
        site_model_abs = os.path.abspath(os.path.expanduser(site_model)) if site_model else os.path.join(ROOT_DIR, "sites", "udmi_site_model")
        
        seq_log_abs = os.path.join(site_model_abs, "out", "devices", device_id, "tests", test_id, "sequence.log")
        seq_md_abs = os.path.join(site_model_abs, "out", "devices", device_id, "tests", test_id, "sequence.md")
        pubber_log_abs = os.path.join(ROOT_DIR, "out", "pubber.log")
        udmis_log_abs = os.path.join(ROOT_DIR, "out", "udmis.log")

        if not os.path.exists(seq_log_abs):
            self.send_error_response(412, f"Triage aborted: Sequencer log not found for '{test_id}'. Please run the compliance test case first to generate logs.")
            return

        try:
            with open(seq_log_abs, 'r', encoding='utf-8', errors='ignore') as f:
                log_content = f.read()
                if "RESULT pass" in log_content:
                    response_data = json.dumps({
                        "status": "Skipped",
                        "session_id": session_id,
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

        log_path = os.path.join(session_dir, 'triage.log')

        fetch_udmis = (str(fetch_udmis_param).lower() == 'true') if fetch_udmis_param else False
        is_localhost = 'localhost' in (project_spec or '').lower()
        if fetch_udmis and not is_localhost:
            target_gcp_project = cloud_project_param
            if not target_gcp_project and project_spec:
                clean_spec = project_spec.replace('//gref/', '').replace('//gbos/', '').replace('//mqtt/', '').replace('//', '')
                cand_proj = clean_spec.split('/')[0].split('@')[0]
                if cand_proj and cand_proj.lower() != 'localhost':
                    target_gcp_project = cand_proj

            if target_gcp_project:
                pull_cloud_script = os.path.join(ROOT_DIR, "util", "mantis", "bin", "pull_cloud_logs")
                if os.path.exists(pull_cloud_script):
                    try:
                        ts_str = datetime.now().strftime('%H:%M:%S')
                        with open(log_path, 'a', encoding='utf-8') as lf:
                            lf.write(f"[{ts_str}] ☁️ Fetching UDMIS container logs from Google Cloud Logging for project '{target_gcp_project}'...\n")
                        cmd_pull = [python_bin, pull_cloud_script, "-sl", seq_log_abs, "-t", test_id, "-p", target_gcp_project, "-o", udmis_log_abs]
                        res_pull = subprocess.run(cmd_pull, capture_output=True, text=True, timeout=60)
                        ts_str2 = datetime.now().strftime('%H:%M:%S')
                        with open(log_path, 'a', encoding='utf-8') as lf:
                            if res_pull.returncode == 0:
                                lf.write(f"[{ts_str2}] ✅ Successfully fetched Cloud Logging payload into session.\n\n")
                            else:
                                lf.write(f"[{ts_str2}] ⚠️ Cloud logging fetch warning: {res_pull.stderr or res_pull.stdout}\n\n")
                    except Exception as e:
                        ts_str3 = datetime.now().strftime('%H:%M:%S')
                        with open(log_path, 'a', encoding='utf-8') as lf:
                            lf.write(f"[{ts_str3}] ⚠️ Failed to fetch UDMIS cloud logs: {e}\n\n")

        seq_log_rel = os.path.relpath(seq_log_abs, ROOT_DIR)
        seq_md_rel = os.path.relpath(seq_md_abs, ROOT_DIR)
        pubber_log_rel = os.path.relpath(pubber_log_abs, ROOT_DIR)
        udmis_log_rel = os.path.relpath(udmis_log_abs, ROOT_DIR)

        failed_run_logs = {
            "sequence_log": seq_log_rel
        }

        if os.path.exists(seq_md_abs):
            failed_run_logs["sequence_md"] = seq_md_rel
        if os.path.exists(pubber_log_abs):
            failed_run_logs["pubber_log"] = pubber_log_rel
        if os.path.exists(udmis_log_abs):
            failed_run_logs["udmis_log"] = udmis_log_rel

        success_run_param = data.get('success_run') or params.get('success_run', [None])[0]

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

        if success_run_param:
            succ_target = os.path.abspath(os.path.expanduser(success_run_param)) if os.path.isabs(os.path.expanduser(success_run_param)) else os.path.abspath(os.path.join(ROOT_DIR, success_run_param))
            succ_log_abs = None
            if os.path.isdir(succ_target):
                cand1 = os.path.join(succ_target, "sequence.log")
                cand2 = os.path.join(succ_target, "out", "devices", device_id, "tests", test_id, "sequence.log")
                if os.path.exists(cand1):
                    succ_log_abs = cand1
                elif os.path.exists(cand2):
                    succ_log_abs = cand2
            elif os.path.isfile(succ_target) and succ_target.endswith(".log"):
                succ_log_abs = succ_target

            if succ_log_abs and os.path.exists(succ_log_abs):
                succ_log_rel = os.path.relpath(succ_log_abs, ROOT_DIR)
                succ_md_abs = succ_log_abs.replace(".log", ".md")
                succ_logs = {"sequence_log": succ_log_rel}
                if os.path.exists(succ_md_abs):
                    succ_logs["sequence_md"] = os.path.relpath(succ_md_abs, ROOT_DIR)
                manifest_data["failures"][0]["logs"]["success_run"] = succ_logs

        manifest_path = os.path.join(session_dir, 'triage_manifest.json')
        try:
            with open(manifest_path, 'w', encoding='utf-8') as fm:
                json.dump(manifest_data, fm, indent=2)
        except Exception as e:
            print(f"Warning: failed to write triage_manifest.json: {e}")

        manifest_relative_path = os.path.relpath(manifest_path, ROOT_DIR)
        cmd = [python_bin, "-u", "-m", "mantis.src.app.main", "-m", manifest_relative_path, "-d", device_id, "-t", test_id]

        if playbook == "swe":
            playbook_path = os.path.join(ROOT_DIR, 'util', 'mantis', 'config', 'playbook_swe.yaml')
            cmd.extend(["--playbook", playbook_path])

        env = os.environ.copy()
        mantis_src = os.path.join(ROOT_DIR, 'util', 'mantis', 'src')
        util_dir = os.path.join(ROOT_DIR, 'util')
        tools_dir = os.path.join(ROOT_DIR, 'tools')
        env['PYTHONPATH'] = f"{tools_dir}:{mantis_src}:{util_dir}:{env.get('PYTHONPATH', '')}"

        if gemini_key:
            env['GEMINI_API_KEY'] = gemini_key
        if use_vertex:
            env['MANTIS_USE_VERTEXAI'] = 'true'
            if gcp_project_param:
                env['GCLOUD_PROJECT'] = gcp_project_param
            if gcp_location_param:
                env['GCP_LOCATION'] = gcp_location_param

        log_path = os.path.join(session_dir, 'triage.log')

        try:
            proc = subprocess.Popen(
                cmd,
                cwd=ROOT_DIR,
                env=env,
                stdout=open(log_path, 'ab', buffering=0),
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            
            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "triage",
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            response_data = json.dumps({
                "status": "Started",
                "session_id": session_id,
                "pid": proc.pid,
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

    def handle_stop_triage(self, query_string=None, post_data=None):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        data = post_data or {}
        session_id = data.get('session_id') or params.get('session_id', [None])[0]

        target_meta = None
        target_sid = None
        with active_processes_lock:
            if session_id and session_id in active_processes:
                target_sid = session_id
                target_meta = active_processes[session_id]
            else:
                target_sid, target_meta = get_latest_session_process('triage')

        if not target_meta or not target_meta.get('process') or target_meta['process'].poll() is not None:
            response_data = json.dumps({"status": "Not running"}).encode('utf-8')
        else:
            proc = target_meta['process']
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
                proc.wait(timeout=2)
                response_data = json.dumps({"status": "Stopped", "session_id": target_sid}).encode('utf-8')
            except Exception as e:
                try:
                    proc.kill()
                    response_data = json.dumps({"status": "Stopped (fallback)", "session_id": target_sid, "error": str(e)}).encode('utf-8')
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
        params = urllib.parse.parse_qs(query_string)
        session_id = params.get('session_id', [None])[0]
        offset_param = params.get('offset', [0])[0]
        
        try:
            offset = int(offset_param)
        except Exception:
            offset = 0

        target_meta = None
        target_sid = session_id
        if target_sid:
            with active_processes_lock:
                target_meta = active_processes.get(target_sid)
        else:
            target_sid, target_meta = get_latest_session_process('triage')

        proc = target_meta.get('process') if target_meta else None
        is_running = proc is not None and proc.poll() is None
        exit_code = proc.poll() if proc else None

        log_content = ""
        new_offset = offset
        log_path = target_meta.get('log_path') if target_meta else (os.path.join(ROOT_DIR, 'out', 'sessions', target_sid, 'triage.log') if target_sid else os.path.join(ROOT_DIR, 'out', 'triage.log'))

        if log_path and os.path.exists(log_path):
            try:
                with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
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
            "session_id": target_sid,
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
        session_id = params.get('session_id', [None])[0]
        site_model = params.get('site_model', [None])[0]
        project_spec = params.get('project_spec', [None])[0]
        device_id = params.get('device_id', [None])[0]
        test_id = params.get('test_id', [None])[0]

        if not all([site_model, project_spec, device_id, test_id]):
            self.send_error_response(400, "Missing required parameters: site_model, project_spec, device_id, test_id")
            return

        clean_target = project_spec.replace("/", "_").replace("+", "_").strip("_")
        site_model_resolved = os.path.expanduser(site_model)
        site_id = os.path.basename(os.path.normpath(site_model_resolved))
        
        report_path = None
        if session_id:
            report_path = os.path.join(ROOT_DIR, 'out', 'sessions', session_id, 'diagnose', clean_target, site_id, device_id, test_id, 'triage_analysis.md')
        
        if not report_path or not os.path.exists(report_path):
            cand = os.path.join(ROOT_DIR, 'out', 'diagnose', clean_target, site_id, device_id, test_id, 'triage_analysis.md')
            if os.path.exists(cand):
                report_path = cand
            else:
                sessions_dir = os.path.join(ROOT_DIR, 'out', 'sessions')
                if os.path.exists(sessions_dir):
                    for s in sorted(os.listdir(sessions_dir), reverse=True):
                        s_cand = os.path.join(sessions_dir, s, 'diagnose', clean_target, site_id, device_id, test_id, 'triage_analysis.md')
                        if os.path.exists(s_cand):
                            report_path = s_cand
                            break

        if not report_path or not os.path.exists(report_path):
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
    for arg in sys.argv:
        if arg.startswith('--features='):
            features_str = arg.split('=', 1)[1].lower()
            ALLOWED_FEATURES = set(f.strip() for f in features_str.split(',') if f.strip())

    print(f"Starting UDMI custom API & Static server on port {PORT} serving directory {ROOT_DIR}")
    print(f"Enforced server-side features: {list(ALLOWED_FEATURES)}")
    prune_old_sessions(10)
    
    try:
        server = HTTPServer(('0.0.0.0', PORT), UDMIRequestHandler)
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
        sys.exit(0)
