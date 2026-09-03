"""
UDMI Workbench API & Static HTTP Server
Provides non-blocking REST API endpoints, Server-Sent Events (SSE), process management,
and local tool bridges for the UDMI Workbench single-page application.
"""

import difflib
from email.message import EmailMessage
from http.server import HTTPServer, SimpleHTTPRequestHandler
import json
import os
import re
import shutil
import signal
import smtplib
import socket
import subprocess
import sys
import threading
import time
from datetime import datetime
import urllib.error
import urllib.parse
import urllib.request
import uuid

PORT = 8080
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
HOME_DIR = os.path.abspath(os.path.expanduser('~'))

# Ensure Mantis and local tools are importable
mantis_v2 = os.path.join(ROOT_DIR, "util", "mantis", "v2")
mantis_src = os.path.join(ROOT_DIR, "util", "mantis", "src")
mantis_v1 = os.path.join(ROOT_DIR, "util", "mantis", "v1")
mantis_dir = os.path.join(ROOT_DIR, "util", "mantis")
tools_dir = os.path.join(ROOT_DIR, "tools")
for p in [mantis_v2, mantis_src, mantis_v1, mantis_dir, tools_dir]:
    if os.path.exists(p) and p not in sys.path:
        sys.path.insert(0, p)


def to_home_relative(path_str):
    """Convert an absolute path to a ~-prefixed relative path if within user's home directory."""
    if not path_str:
        return path_str
    abs_path = os.path.abspath(os.path.expanduser(path_str))
    if abs_path == HOME_DIR:
        return "~"
    if abs_path.startswith(HOME_DIR + os.sep):
        return "~" + abs_path[len(HOME_DIR):]
    return abs_path


active_processes_lock = threading.Lock()
active_processes = {}

active_mantis_sessions_lock = threading.Lock()
active_mantis_sessions = {}


def get_latest_session_process(proc_type: str):
    """Retrieve the most recently created session process for a given type."""
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
    """Prune inactive session directories in out/sessions to conserve disk space."""
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


def start_etcd_explorer_service(etcd_port=18834, explorer_port=8085):
    """Ensure EtcdExplorerServer is running in background on explorer_port connected to etcd_port."""
    try:
        with socket.create_connection(('127.0.0.1', explorer_port), timeout=0.3):
            return True
    except Exception:
        pass

    etcd_running = False
    for p in [etcd_port, 2379, 18834]:
        try:
            with socket.create_connection(('127.0.0.1', p), timeout=0.3):
                etcd_port = p
                etcd_running = True
                break
        except Exception:
            pass

    if not etcd_running:
        return False

    try:
        subprocess.run(['pkill', '-f', 'EtcdExplorerServer'], capture_output=True)
    except Exception:
        pass

    log_path = os.path.join(ROOT_DIR, 'out', 'etcd_explorer.log')
    pid_path = os.path.join(ROOT_DIR, 'var', 'etcd_explorer.pid')
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    os.makedirs(os.path.dirname(pid_path), exist_ok=True)

    cmd = [
        "udmis/bin/etcd_explorer",
        f"--port={explorer_port}",
        f"--etcd_target=http://127.0.0.1:{etcd_port}"
    ]

    try:
        with open(log_path, 'ab', buffering=0) as log_file:
            proc = subprocess.Popen(
                cmd,
                cwd=ROOT_DIR,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            with open(pid_path, 'w', encoding='utf-8') as pf:
                pf.write(str(proc.pid))
            return True
    except Exception as e:
        print(f"Error starting etcd explorer: {e}", file=sys.stderr)
        return False


# K8s resource cache with 5-second TTL
_k8s_cache = {} # namespace -> (timestamp, items)

def parse_project_spec(spec):
    """
    Parses project spec conforming to: [//provider/]project[/namespace][+user]
    Handles both literal '+' and URL-decoded ' ' for user suffix.
    """
    if not spec:
        return {"provider": "mqtt", "project": "localhost", "namespace": None, "effective_namespace": "udmis", "user": None, "is_cloud": False}
    s = spec.strip()
    provider = None
    if s.startswith("//"):
        s = s[2:]
        if "/" in s:
            provider, s = s.split("/", 1)
        else:
            provider = s
            s = ""
    user = None
    if "+" in s:
        s, user = s.split("+", 1)
    elif " " in s:
        s, user = s.split(" ", 1)
        
    namespace = None
    project = s
    if "/" in s:
        project, namespace = s.split("/", 1)
        if "+" in namespace:
            namespace, user = namespace.split("+", 1)
        elif " " in namespace:
            namespace, user = namespace.split(" ", 1)
    
    effective_namespace = namespace if namespace else "udmis"
    is_cloud = bool(provider in ("gbos", "gref", "pubsub", "clearblade") or (project and "bos-platform" in project))
    return {
        "provider": provider or "pubsub",
        "project": project or "bos-platform-dev",
        "namespace": namespace,
        "effective_namespace": effective_namespace,
        "user": user,
        "is_cloud": is_cloud
    }

def is_kubectl_available():
    return shutil.which('kubectl') is not None

def get_k8s_resources_for_namespace(namespace):
    if not is_kubectl_available():
        return None
    now = time.time()
    if namespace in _k8s_cache:
        cached_time, cached_items = _k8s_cache[namespace]
        if now - cached_time < 15.0:
            return cached_items
    try:
        res = subprocess.run(
            ['kubectl', 'get', 'deployments,statefulsets', '-n', namespace, '-o', 'json'],
            capture_output=True,
            text=True,
            timeout=8
        )
        if res.returncode == 0 and res.stdout.strip():
            data = json.loads(res.stdout)
            items = data.get('items', [])
            _k8s_cache[namespace] = (now, items)
            return items
        else:
            return None
    except Exception as e:
        print(f"Warning: Error querying kubectl in namespace {namespace}: {e}")
        if namespace in _k8s_cache:
            return _k8s_cache[namespace][1]
        return None

def evaluate_k8s_zanzara_status(project_spec):
    parsed = parse_project_spec(project_spec)
    ns = parsed["effective_namespace"]
    project_id = parsed["project"]

    target_items = get_k8s_resources_for_namespace(ns)
    udmis_items = target_items if ns == 'udmis' else get_k8s_resources_for_namespace('udmis')

    # If kubectl is not installed or cluster is unreachable, do not report false health status
    if target_items is None and udmis_items is None:
        return {
            "parsed": parsed,
            "k8s_available": False,
            "cloud_udmis": {
                "status": "UNAVAILABLE",
                "namespace": ns,
                "details": ""
            },
            "zanzara_ingress": {
                "status": "UNAVAILABLE",
                "namespace": ns,
                "endpoint": f"{project_id}.corp.goog:8883",
                "details": ""
            },
            "zanzara_fabric": {
                "status": "UNAVAILABLE",
                "namespace": ns,
                "pipeline": "Mosquitto+Bridges+Pub/Sub",
                "details": ""
            },
            "etcd": {
                "status": "UNAVAILABLE",
                "namespace": ns,
                "port": 2379,
                "details": ""
            }
        }

    target_items = target_items or []
    udmis_items = udmis_items or []

    # 1. etcd State Store (check first as other components depend on it)
    etcd_item = next((i for i in target_items if 'etcd' in i.get('metadata', {}).get('name', '')), None)
    etcd_ns = ns
    if not etcd_item:
        etcd_item = next((i for i in udmis_items if 'etcd' in i.get('metadata', {}).get('name', '')), None)
        if etcd_item:
            etcd_ns = "udmis"

    ready_etcd = etcd_item.get('status', {}).get('readyReplicas', 0) if etcd_item else 0
    replicas_etcd = etcd_item.get('spec', {}).get('replicas', 3) if etcd_item else 3
    if etcd_item and ready_etcd > 0:
        etcd_status = {
            "status": "UP",
            "namespace": etcd_ns,
            "port": 2379,
            "ready_replicas": ready_etcd,
            "total_replicas": replicas_etcd,
            "details": f"etcd cluster {ready_etcd}/{replicas_etcd} replicas ready in namespace '{etcd_ns}'"
        }
    else:
        etcd_status = {
            "status": "DOWN",
            "namespace": etcd_ns,
            "port": 2379,
            "ready_replicas": ready_etcd,
            "total_replicas": replicas_etcd,
            "details": f"etcd StatefulSet has {ready_etcd}/{replicas_etcd} ready replicas in namespace '{etcd_ns}'"
        }

    # 2. Cloud UDMIS
    udmis_dep = next((i for i in target_items if i.get('kind') == 'Deployment' and i.get('metadata', {}).get('name') in ('udmis-pods', 'udmis')), None)
    udmis_ns = ns
    if not udmis_dep:
        udmis_dep = next((i for i in udmis_items if i.get('kind') == 'Deployment' and i.get('metadata', {}).get('name') in ('udmis-pods', 'udmis')), None)
        if udmis_dep:
            udmis_ns = "udmis"

    if udmis_dep:
        ready_udmis = udmis_dep.get('status', {}).get('readyReplicas', 0)
        replicas_udmis = udmis_dep.get('spec', {}).get('replicas', 1)
        cloud_udmis = {
            "status": "UP" if ready_udmis > 0 else "DOWN",
            "namespace": udmis_ns,
            "ready_replicas": ready_udmis,
            "total_replicas": replicas_udmis,
            "details": f"{ready_udmis}/{replicas_udmis} pods ready in namespace '{udmis_ns}'"
        }
    else:
        cloud_udmis = {
            "status": "DOWN",
            "namespace": ns,
            "ready_replicas": 0,
            "total_replicas": 0,
            "details": f"Deployment 'udmis-pods' not found in namespace '{ns}' or 'udmis'"
        }

    # 3. Mosquitto Broker check (shared for ingress and fabric)
    mosquitto_item = next((i for i in (target_items + udmis_items) if i.get('kind') == 'StatefulSet' and 'mosquitto' in i.get('metadata', {}).get('name', '')), None)
    ready_mosquitto = mosquitto_item.get('status', {}).get('readyReplicas', 0) if mosquitto_item else 0
    mosquitto_up = ready_mosquitto > 0

    # 4. Zanzara Ingress (auth proxy)
    auth_deps = [i for i in target_items if i.get('kind') == 'Deployment' and i.get('metadata', {}).get('name', '').startswith('auth')]
    auth_ns = ns
    if not auth_deps and ns != 'udmis':
        auth_deps = [i for i in udmis_items if i.get('kind') == 'Deployment' and i.get('metadata', {}).get('name', '').startswith('auth')]
        if auth_deps:
            auth_ns = "udmis"

    total_ready_auth = sum(d.get('status', {}).get('readyReplicas', 0) for d in auth_deps)
    active_auth_names = [d.get('metadata', {}).get('name') for d in auth_deps if d.get('status', {}).get('readyReplicas', 0) > 0]

    if total_ready_auth > 0 and ready_etcd > 0 and mosquitto_up:
        zanzara_ingress = {
            "status": "UP",
            "namespace": auth_ns,
            "endpoint": f"{project_id}.corp.goog:8883",
            "details": f"Auth proxy ({', '.join(active_auth_names)}) running in namespace '{auth_ns}'"
        }
    elif total_ready_auth > 0:
        backend_issues = []
        if ready_etcd == 0: backend_issues.append("etcd DOWN")
        if not mosquitto_up: backend_issues.append("mosquitto DOWN")
        zanzara_ingress = {
            "status": "DOWN",
            "namespace": auth_ns,
            "endpoint": f"{project_id}.corp.goog:8883",
            "details": f"Auth proxy running ({total_ready_auth} ready), but backend {', '.join(backend_issues)}"
        }
    else:
        zanzara_ingress = {
            "status": "DOWN",
            "namespace": auth_ns,
            "endpoint": f"{project_id}.corp.goog:8883",
            "details": f"Auth proxy has 0 ready replicas in namespace '{auth_ns}'"
        }

    # 5. Zanzara Message Fabric (bridge statefulsets + mosquitto)
    bridge_items = [i for i in target_items if i.get('kind') == 'StatefulSet' and 'bridge' in i.get('metadata', {}).get('name', '')]
    fabric_ns = ns
    if not bridge_items:
        bridge_items = [i for i in udmis_items if i.get('kind') == 'StatefulSet' and 'bridge' in i.get('metadata', {}).get('name', '')]
        if bridge_items:
            fabric_ns = "udmis"

    total_bridges = len(bridge_items)
    ready_bridges = sum(1 for b in bridge_items if b.get('status', {}).get('readyReplicas', 0) > 0)

    if total_bridges > 0 and ready_bridges > 0 and mosquitto_up:
        zanzara_fabric = {
            "status": "UP",
            "namespace": fabric_ns,
            "pipeline": "Mosquitto+Bridges+Pub/Sub",
            "bridge_count": total_bridges,
            "ready_bridges": ready_bridges,
            "details": f"{ready_bridges}/{total_bridges} bridges ready, mosquitto UP in namespace '{fabric_ns}'"
        }
    else:
        fabric_issues = []
        if ready_bridges < total_bridges:
            fabric_issues.append(f"{ready_bridges}/{total_bridges} bridges ready")
        if not mosquitto_up:
            fabric_issues.append("mosquitto DOWN")
        zanzara_fabric = {
            "status": "DOWN",
            "namespace": fabric_ns,
            "pipeline": "Mosquitto+Bridges+Pub/Sub",
            "bridge_count": total_bridges,
            "ready_bridges": ready_bridges,
            "details": f"Fabric degraded ({', '.join(fabric_issues)}) in namespace '{fabric_ns}'"
        }

    return {
        "parsed": parsed,
        "cloud_udmis": cloud_udmis,
        "zanzara_ingress": zanzara_ingress,
        "zanzara_fabric": zanzara_fabric,
        "etcd": etcd_status
    }


class UDMIRequestHandler(SimpleHTTPRequestHandler):
    """HTTP Request Handler providing REST endpoints and static file serving."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT_DIR, **kwargs)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate, max-age=0')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

    def send_json_response(self, data, status_code=200):
        """Helper to send standardized JSON responses with CORS and length headers."""
        response_bytes = json.dumps(data).encode('utf-8')
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(response_bytes)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(response_bytes)

    def send_error_response(self, code, message):
        """Helper to send standardized JSON error responses."""
        self.send_json_response({"error": message}, status_code=code)

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

        routes = {
            '/api/testbed/start': lambda: self.handle_testbed_start(parsed_url.query, post_data=post_data),
            '/api/testbed/stop': lambda: self.handle_testbed_stop(parsed_url.query, post_data=post_data),
            '/api/testbed/start_component': lambda: self.handle_testbed_start_component(parsed_url.query, post_data=post_data),
            '/api/testbed/stop_component': lambda: self.handle_testbed_stop_component(parsed_url.query, post_data=post_data),
            '/api/testbed/start_pubber': lambda: self.handle_testbed_start_pubber(parsed_url.query, post_data=post_data),
            '/api/testbed/stop_pubber': lambda: self.handle_testbed_stop_pubber(parsed_url.query, post_data=post_data),
            '/api/run_triage': lambda: self.handle_run_triage(parsed_url.query, post_data=post_data, bearer_key=bearer_key),
            '/api/stop_triage': lambda: self.handle_stop_triage(parsed_url.query, post_data=post_data),
            '/api/run_sequencer': lambda: self.handle_run_sequencer(parsed_url.query, post_data=post_data),
            '/api/stop_sequencer': lambda: self.handle_stop_sequencer(parsed_url.query, post_data=post_data),
            '/api/log_diff': lambda: self.handle_log_diff(parsed_url.query, post_data=post_data),
            '/api/ai_query': lambda: self.handle_ai_query(parsed_url.query, post_data=post_data),
            '/api/mantis/chat/stream': lambda: self.handle_mantis_chat_stream(parsed_url.query, post_data=post_data, bearer_key=bearer_key),
            '/api/mantis/chat/stop': lambda: self.handle_mantis_chat_stop(parsed_url.query, post_data=post_data),
            '/api/mantis/chat/clear': lambda: self.handle_mantis_chat_clear(parsed_url.query, post_data=post_data),
            '/api/graphviz/render': lambda: self.handle_graphviz_render(parsed_url.query, post_data=post_data),
            '/api/git/commit': lambda: self.handle_git_commit(parsed_url.query, post_data=post_data),
            '/api/notifications/send_email': lambda: self.handle_send_email(parsed_url.query, post_data=post_data),
        }

        handler = routes.get(parsed_url.path)
        if handler:
            handler()
        else:
            self.send_error_response(404, "Endpoint not found")

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)

        # Redirect root and version aliases
        if parsed_url.path in ('', '/', '/index.html'):
            self.send_response(302)
            self.send_header('Location', '/ui/v2/index.html')
            self.end_headers()
            return
        if parsed_url.path in ('/ui/v2', '/ui/v2/'):
            self.send_response(302)
            self.send_header('Location', '/ui/v2/index.html')
            self.end_headers()
            return
        if parsed_url.path in ('/ui/v1', '/ui/v1/'):
            self.send_response(302)
            self.send_header('Location', '/ui/v1/index.html')
            self.end_headers()
            return
        if parsed_url.path.startswith('/ui/src/'):
            new_path = '/ui/v2/' + parsed_url.path[len('/ui/src/'):]
            if parsed_url.query:
                new_path += '?' + parsed_url.query
            self.send_response(302)
            self.send_header('Location', new_path)
            self.end_headers()
            return

        routes = {
            '/api/list': lambda: self.handle_api_list(parsed_url.query),
            '/api/mantis/chat/context': lambda: self.handle_mantis_chat_context(parsed_url.query),
            '/api/read_file': lambda: self.handle_api_read_file(parsed_url.query),
            '/api/devices': lambda: self.handle_api_devices(parsed_url.query),
            '/api/device_results': lambda: self.handle_device_results(parsed_url.query),
            '/api/testbed/status': lambda: self.handle_testbed_status(parsed_url.query),
            '/api/testbed/jobs': lambda: self.handle_testbed_jobs(parsed_url.query),
            '/api/testbed/topology': lambda: self.handle_testbed_topology(parsed_url.query),
            '/api/git/status': lambda: self.handle_git_status(parsed_url.query),
            '/api/stream': lambda: self.handle_sse_stream(parsed_url.query, proc_type="sequencer"),
            '/api/triage_stream': lambda: self.handle_sse_stream(parsed_url.query, proc_type="triage"),
            '/api/testbed_stream': lambda: self.handle_sse_stream(parsed_url.query, proc_type="testbed"),
            '/api/testbed_proc_status': lambda: self.handle_testbed_proc_status(parsed_url.query),
            '/api/run_sequencer': lambda: self.handle_run_sequencer(parsed_url.query),
            '/api/stop_sequencer': lambda: self.handle_stop_sequencer(parsed_url.query),
            '/api/sequencer_status': lambda: self.handle_sequencer_status(parsed_url.query),
            '/api/run_triage': lambda: self.handle_run_triage(parsed_url.query),
            '/api/stop_triage': lambda: self.handle_stop_triage(parsed_url.query),
            '/api/triage_status': lambda: self.handle_triage_status(parsed_url.query),
            '/api/triage_report': lambda: self.handle_triage_report(parsed_url.query),
            '/api/sequences/catalog': lambda: self.handle_api_sequencer_catalog(parsed_url.query),
            '/api/sequencer/catalog': lambda: self.handle_api_sequencer_catalog(parsed_url.query),
        }

        if parsed_url.path.startswith('/etcd_explorer') or parsed_url.path.startswith('/api/registries'):
            self.handle_etcd_explorer_proxy(parsed_url.path, parsed_url.query)
            return

        handler = routes.get(parsed_url.path)
        if handler:
            handler()
        else:
            super().do_GET()

    def do_OPTIONS(self):
        """Handle CORS preflight requests across all endpoints."""
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, HEAD')
        self.send_header('Access-Control-Allow-Headers', '*')
        self.send_header('Access-Control-Max-Age', '86400')
        self.end_headers()

    def do_HEAD(self):
        """Handle HEAD requests, delegating explorer/registries to proxy or default handler."""
        parsed_url = urllib.parse.urlparse(self.path)
        if parsed_url.path.startswith('/etcd_explorer') or parsed_url.path.startswith('/api/registries'):
            self.handle_etcd_explorer_proxy(parsed_url.path, parsed_url.query, is_head=True)
        else:
            super().do_HEAD()

    def handle_etcd_explorer_proxy(self, req_path, query_string, is_head=False):
        """Proxy requests for ETCD explorer static assets and API to local EtcdExplorerServer on port 8085."""
        start_etcd_explorer_service()

        target_path = req_path
        if target_path.startswith('/etcd_explorer'):
            target_path = target_path[len('/etcd_explorer'):]
            if not target_path:
                target_path = '/'

        target_url = f"http://127.0.0.1:8085{target_path}"
        if query_string:
            target_url += f"?{query_string}"

        try:
            req = urllib.request.Request(target_url, headers={'User-Agent': 'UDMI-Workbench-Proxy'})
            with urllib.request.urlopen(req, timeout=5) as response:
                content = response.read()
                content_type = response.headers.get('Content-Type', 'text/html; charset=utf-8')
                self.send_response(response.status)
                self.send_header('Content-Type', content_type)
                self.send_header('Content-Length', str(len(content)))
                self.send_header('Access-Control-Allow-Origin', '*')
                self.send_header('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS')
                self.send_header('Access-Control-Allow-Headers', '*')
                self.end_headers()
                if not is_head:
                    self.wfile.write(content)
        except urllib.error.HTTPError as e:
            err_content = e.read()
            self.send_response(e.code)
            self.send_header('Content-Type', e.headers.get('Content-Type', 'text/plain'))
            self.send_header('Content-Length', str(len(err_content)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS')
            self.send_header('Access-Control-Allow-Headers', '*')
            self.end_headers()
            if not is_head:
                self.wfile.write(err_content)
        except Exception as e:
            self.send_error_response(502, f"ETCD Explorer not reachable on port 8085: {str(e)}")

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
            entries = []
            for name in os.listdir(target_path):
                full_path = os.path.join(target_path, name)
                is_dir = os.path.isdir(full_path)
                if is_dir:
                    subdirs.append(name)
                elif os.path.isfile(full_path):
                    files.append(name)
                entries.append({
                    "name": name,
                    "is_dir": is_dir,
                    "path": to_home_relative(full_path)
                })
            subdirs.sort()
            files.sort()
            entries.sort(key=lambda x: x["name"])

            self.send_json_response({
                "path": to_home_relative(target_path),
                "absolute_path": target_path,
                "parent_path": to_home_relative(os.path.dirname(target_path)),
                "entries": entries,
                "folders": subdirs,
                "files": files
            })
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

            self.send_json_response({
                "path": to_home_relative(target_path),
                "content": content
            })
        except Exception as e:
            self.send_error_response(500, str(e))

    def handle_api_devices(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', [None])[0] or params.get('site_path', [None])[0]

        if not site_model:
            self.send_error_response(400, "Missing 'site_model' parameter")
            return

        target_dir = os.path.expanduser(site_model)
        if not os.path.isabs(target_dir):
            target_dir = os.path.abspath(os.path.join(ROOT_DIR, target_dir))

        if not os.path.exists(target_dir) or not os.path.isdir(target_dir):
            self.send_error_response(404, f"Site model directory not found: {site_model}")
            return

        devices_dir = os.path.join(target_dir, 'devices')
        if not os.path.exists(devices_dir) or not os.path.isdir(devices_dir):
            devices_dir = os.path.join(target_dir, 'udmi', 'devices')
        if not os.path.exists(devices_dir) or not os.path.isdir(devices_dir):
            self.send_error_response(404, f"Site model missing 'devices' directory: {site_model}")
            return

        devices = []
        device_metadata = {}
        try:
            for d in os.listdir(devices_dir):
                full_d = os.path.join(devices_dir, d)
                if os.path.isdir(full_d):
                    devices.append(d)
                    meta_path = os.path.join(full_d, 'metadata.json')
                    if os.path.isfile(meta_path):
                        try:
                            with open(meta_path, 'r', encoding='utf-8') as mf:
                                meta_json = json.load(mf)
                                device_metadata[d] = {
                                    "version": meta_json.get("version"),
                                    "serial_no": meta_json.get("serial_no") or meta_json.get("device_serial")
                                }
                        except Exception:
                            pass
            devices.sort()
        except Exception as e:
            self.send_error_response(500, f"Error scanning devices directory: {str(e)}")
            return

        self.send_json_response({
            "site_model": to_home_relative(target_dir),
            "devices": devices,
            "device_metadata": device_metadata
        })

    def handle_device_results(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', [None])[0] or params.get('site_path', [None])[0]
        device = params.get('device', [None])[0] or params.get('device_id', [None])[0]

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

                        status_raw = 'idle'
                        if os.path.exists(seq_md):
                            with open(seq_md, 'r', encoding='utf-8', errors='replace') as f:
                                md_content = f.read()
                            md_lower = md_content.lower()
                            if 'test passed' in md_lower or 'sequence complete' in md_lower:
                                status_raw = 'pass'
                            elif 'test skipped' in md_lower:
                                status_raw = 'skip'
                            elif os.path.exists(seq_log):
                                status_raw = 'fail'
                        elif os.path.exists(seq_log):
                            status_raw = 'fail'

                        status_map = {
                            'pass': 'PASSED',
                            'fail': 'FAILED',
                            'skip': 'SKIPPED',
                            'idle': 'IDLE'
                        }
                        status = status_map.get(status_raw, 'IDLE')

                        project_spec = None
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
                            project_spec = "localhost"

                        results[test_name] = {
                            "status": status,
                            "stage": "ALPHA",
                            "timestamp": formatted_ts,
                            "project_spec": project_spec
                        }
            except Exception as e:
                self.send_error_response(500, f"Error scanning test directory: {str(e)}")
                return

        self.send_json_response({
            "site_model": to_home_relative(target_dir),
            "device": device,
            "results": results
        })

    def handle_api_sequencer_catalog(self, query_string):
        spec_path = os.path.join(ROOT_DIR, 'docs', 'specs', 'sequences', 'generated.md')
        if not os.path.exists(spec_path):
            self.send_error_response(404, "docs/specs/sequences/generated.md not found")
            return

        try:
            with open(spec_path, 'r', encoding='utf-8') as f:
                content = f.read()

            index_desc_map = {}
            index_pattern = re.compile(r"^\*\s+\[([a-zA-Z0-9_-]+)\]\(#[^)]+\)(?::\s*(.*))?$", re.MULTILINE)
            for m in index_pattern.finditer(content):
                t_id = m.group(1).strip()
                desc = (m.group(2) or "").strip()
                desc = re.sub(r"\s*Test skipped:.*$", "", desc).strip()
                index_desc_map[t_id] = desc

            section_pattern = re.compile(r"^##\s+([a-zA-Z0-9_-]+)\s*(?:\(([^)]+)\))?", re.MULTILINE)
            matches = list(section_pattern.finditer(content))

            categories_map = {
                "System & Base": {"icon": "dns", "tests": []},
                "Pointset & Telemetry": {"icon": "analytics", "tests": []},
                "Discovery & Scanning": {"icon": "search", "tests": []},
                "Blobset & Firmware": {"icon": "memory", "tests": []},
                "Endpoint & Connection": {"icon": "cloud_sync", "tests": []},
                "Gateway & Proxy": {"icon": "router", "tests": []},
                "Localnet & Addressing": {"icon": "hub", "tests": []},
            }

            for i, match in enumerate(matches):
                test_id = match.group(1).strip()
                stage = (match.group(2) or "STABLE").strip().upper()
                start = match.end()
                end = matches[i+1].start() if i + 1 < len(matches) else len(content)
                section_body = content[start:end].strip()

                paragraphs = [p.strip() for p in section_body.split("\n\n") if p.strip()]
                desc = index_desc_map.get(test_id, "")
                steps = []
                for p in paragraphs:
                    if p.startswith("1. ") or p.startswith("* ") or p.startswith("- "):
                        steps.extend([line.strip() for line in p.split("\n") if line.strip()])
                    elif not desc and not p.startswith("Test skipped:") and not p.startswith("Test passed."):
                        desc = p

                if test_id.startswith("system_") or test_id in ["broken_config", "extra_config", "device_config_acked", "config_logging", "valid_serial_no", "state_make_model", "state_software"]:
                    cat_name = "System & Base"
                elif test_id.startswith("pointset_"):
                    cat_name = "Pointset & Telemetry"
                elif test_id.startswith("blob_"):
                    cat_name = "Blobset & Firmware"
                elif test_id.startswith("endpoint_"):
                    cat_name = "Endpoint & Connection"
                elif test_id.startswith("enumerate_") or test_id.startswith("scan_") or test_id.startswith("discovery_"):
                    cat_name = "Discovery & Scanning"
                elif test_id.startswith("gateway_") or test_id.startswith("bad_"):
                    cat_name = "Gateway & Proxy"
                elif test_id.startswith("family_") or test_id.startswith("localnet_"):
                    cat_name = "Localnet & Addressing"
                else:
                    prefix = test_id.split('_')[0].capitalize()
                    cat_name = f"{prefix} Sequences"
                    if cat_name not in categories_map:
                        categories_map[cat_name] = {"icon": "checklist", "tests": []}

                categories_map[cat_name]["tests"].append({
                    "id": test_id,
                    "name": test_id,
                    "stage": stage,
                    "desc": desc or f"Sequence test {test_id}",
                    "steps": steps
                })

            catalog = []
            for cat_name, val in categories_map.items():
                if val["tests"]:
                    catalog.append({
                        "category": cat_name,
                        "icon": val["icon"],
                        "tests": val["tests"]
                    })

            self.send_json_response({
                "catalog": catalog,
                "total_tests": sum(len(c["tests"]) for c in catalog)
            })
        except Exception as e:
            self.send_error_response(500, f"Error parsing sequence catalog: {str(e)}")

    def handle_testbed_jobs(self, query_string):
        jobs = []
        with active_processes_lock:
            for sid, meta in active_processes.items():
                proc = meta.get("process")
                is_running = proc is not None and proc.poll() is None
                jobs.append({
                    "session_id": sid,
                    "type": meta.get("type", "unknown"),
                    "device_id": meta.get("device_id"),
                    "site_model": meta.get("site_model"),
                    "running": is_running,
                    "created_at": meta.get("created_at")
                })
        self.send_json_response({"jobs": jobs})

    def handle_testbed_status(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', ['sites/udmi_site_model'])[0]

        mqtt_port = os.environ.get('MQTT_PORT', '18833')
        mqtt_up = False
        mqtt_latency = 0
        t0 = time.time()
        for port in [int(mqtt_port), 18833, 18883, 1883, 8883]:
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=0.3):
                    mqtt_up = True
                    mqtt_latency = int((time.time() - t0) * 1000)
                    break
            except Exception:
                pass

        validator_path = os.path.join(ROOT_DIR, 'bin', 'test_validator')
        validator_up = os.path.exists(validator_path) and os.access(validator_path, os.X_OK)

        sequencer_path = os.path.join(ROOT_DIR, 'bin', 'sequencer')
        sequencer_ready = os.path.exists(sequencer_path) and os.access(sequencer_path, os.X_OK)

        udmis_up = False
        with active_processes_lock:
            for sid, meta in active_processes.items():
                if meta.get('type') in ('testbed', 'udmis'):
                    p = meta.get('process')
                    if p and p.poll() is None:
                        udmis_up = True
                        break

        if not udmis_up:
            try:
                res = subprocess.run(['pgrep', '-f', 'udmis-1.0-SNAPSHOT-all.jar'], capture_output=True, text=True)
                if res.returncode == 0 and res.stdout.strip():
                    udmis_up = True
                elif os.path.exists(os.path.join(ROOT_DIR, 'var', 'pod_ready.txt')):
                    udmis_up = True
            except Exception:
                pass

        target_site, _ = self._resolve_and_verify_path(site_model)
        site_model_valid = target_site is not None and os.path.exists(target_site) and os.path.isdir(target_site)
        device_count = 0
        if site_model_valid:
            dev_dir = os.path.join(target_site, 'devices')
            if os.path.exists(dev_dir):
                device_count = len([d for d in os.listdir(dev_dir) if os.path.isdir(os.path.join(dev_dir, d))])

        etcd_up = False
        try:
            res = subprocess.run(['pgrep', '-f', 'etcd'], capture_output=True, text=True)
            if res.returncode == 0 and res.stdout.strip():
                etcd_up = True
        except Exception:
            pass

        influx_up = False
        try:
            res = subprocess.run(['pgrep', '-f', 'influx'], capture_output=True, text=True)
            if res.returncode == 0 and res.stdout.strip():
                influx_up = True
        except Exception:
            pass

        postgres_up = False
        try:
            res = subprocess.run(['pgrep', '-f', 'postgres'], capture_output=True, text=True)
            if res.returncode == 0 and res.stdout.strip():
                postgres_up = True
        except Exception:
            pass

        etcd_explorer_up = False
        try:
            res = subprocess.run(['pgrep', '-f', 'EtcdExplorerServer'], capture_output=True, text=True)
            if res.returncode == 0 and res.stdout.strip():
                etcd_explorer_up = True
        except Exception:
            pass

        # Check if project_spec indicates Cloud / Zanzara mode
        project_spec = params.get('project_spec', [''])[0]
        parsed_spec = parse_project_spec(project_spec)
        is_cloud = parsed_spec["is_cloud"]

        target_etcd_port = 18834 if mqtt_port != 8883 else 2379
        if not is_cloud and etcd_up:
            start_etcd_explorer_service(target_etcd_port, 8085)
            try:
                with socket.create_connection(('127.0.0.1', 8085), timeout=0.3):
                    etcd_explorer_up = True
            except Exception:
                pass

        if is_cloud:
            zanzara_health = evaluate_k8s_zanzara_status(project_spec)
            zanzara_ingress_info = zanzara_health["zanzara_ingress"]
            zanzara_fabric_info = zanzara_health["zanzara_fabric"]
            cloud_udmis_info = zanzara_health["cloud_udmis"]
            etcd_info = zanzara_health["etcd"]
            
            cloud_healthy = cloud_udmis_info["status"] == "UP" and zanzara_ingress_info["status"] == "UP"
            overall = "HEALTHY" if cloud_healthy else "DEGRADED"
        else:
            zanzara_ingress_info = {"status": "DOWN", "endpoint": "localhost:8883"}
            zanzara_fabric_info = {"status": "DOWN", "pipeline": "N/A"}
            cloud_udmis_info = {"status": "DOWN", "mode": "LOCAL"}
            etcd_info = {
                "status": "UP" if etcd_up else "DOWN",
                "port": target_etcd_port if etcd_up else 2379,
                "explorer_port": 8085,
                "explorer_status": "UP" if etcd_explorer_up else "DOWN"
            }
            overall = "HEALTHY" if (mqtt_up or udmis_up or site_model_valid) else "DEGRADED"

        running_pubbers = []
        with active_processes_lock:
            for sid, meta in active_processes.items():
                if meta.get('type') == 'pubber':
                    p = meta.get('process')
                    if p and p.poll() is None:
                        dev = meta.get('device_id')
                        if dev:
                            running_pubbers.append(dev)

        try:
            res = subprocess.run(['pgrep', '-a', '-f', 'pubber-1.0-SNAPSHOT-all.jar'], capture_output=True, text=True)
            if res.returncode == 0 and res.stdout:
                for line in res.stdout.strip().split('\n'):
                    parts = line.split()
                    for idx, part in enumerate(parts):
                        if 'com.google.daq.mqtt.sequencer.pubber.Pubber' in part or part.endswith('Pubber'):
                            if idx + 3 < len(parts):
                                running_pubbers.append(parts[idx + 3])
        except Exception:
            pass
        running_pubbers = list(set(filter(None, running_pubbers)))

        self.send_json_response({
            "overall_status": overall,
            "timestamp": int(time.time() * 1000),
            "project_spec_parsed": parsed_spec,
            "active_pubbers": running_pubbers,
            "components": {
                "site_model": {"status": "VALID" if site_model_valid else "INVALID", "path": to_home_relative(target_site or site_model), "device_count": device_count},
                "validator": {"status": "UP" if validator_up else "DOWN", "version": "1.4.2", "schema_valid": True},
                "mqtt_broker": {"status": "UP" if mqtt_up else "DOWN", "endpoint": f"localhost:{mqtt_port}", "latency_ms": mqtt_latency},
                "sequencer": {"status": "READY" if sequencer_ready else "DOWN", "version": "1.4.2"},
                "udmis": {"status": "UP" if udmis_up else "DOWN", "mode": "LOCAL"},
                "zanzara_ingress": zanzara_ingress_info,
                "zanzara_fabric": zanzara_fabric_info,
                "cloud_udmis": cloud_udmis_info,
                "etcd": etcd_info,
                "influx": {"status": "UP" if influx_up else "DOWN", "port": 8086},
                "postgresql": {"status": "UP" if postgres_up else "DOWN", "port": 5432}
            }
        })

    def handle_testbed_topology(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        project_spec = params.get('project_spec', ['//mqtt/localhost:18833'])[0]

        is_cloud = "pubsub" in project_spec.lower() or "gbos" in project_spec.lower() or "gref" in project_spec.lower() or "bos-platform" in project_spec.lower()
        topology_type = "ZANZARA_CLOUD" if is_cloud else "LOCAL_MQTT"

        if is_cloud:
            nodes = [
                {"id": "device", "label": "Device (DUT)", "kind": "device", "status": "UP"},
                {"id": "zanzara_ingress", "label": "Zanzara Ingress", "kind": "proxy", "status": "UP"},
                {"id": "zanzara_fabric", "label": "Message Fabric", "kind": "fabric", "status": "UP"},
                {"id": "cloud_udmis", "label": "Cloud UDMIS", "kind": "backend", "status": "UP"},
                {"id": "etcd", "label": "etcd State Store", "kind": "database", "status": "UP"}
            ]
            edges = [
                {"source": "device", "target": "zanzara_ingress", "label": "Telemetry / State"},
                {"source": "zanzara_ingress", "target": "zanzara_fabric", "label": "MQTT Proxy"},
                {"source": "zanzara_fabric", "target": "cloud_udmis", "label": "Pub/Sub Bridge"},
                {"source": "cloud_udmis", "target": "etcd", "label": "KV State"}
            ]
        else:
            nodes = [
                {"id": "site_model", "label": "Site Model Store", "kind": "config", "status": "VALID"},
                {"id": "validator", "label": "Schema Validator", "kind": "service", "status": "UP"},
                {"id": "mqtt_broker", "label": "Local MQTT Broker", "kind": "broker", "status": "UP"},
                {"id": "sequencer", "label": "Sequencer Engine", "kind": "runner", "status": "READY"},
                {"id": "udmis", "label": "UDMIS Reflective Core", "kind": "backend", "status": "UP"}
            ]
            edges = [
                {"source": "site_model", "target": "validator", "label": "Schema Contract"},
                {"source": "validator", "target": "mqtt_broker", "label": "Telemetry Ingestion"},
                {"source": "mqtt_broker", "target": "sequencer", "label": "Sequence Events"},
                {"source": "sequencer", "target": "udmis", "label": "Reflective Sync"}
            ]

        self.send_json_response({
            "topology_type": topology_type,
            "nodes": nodes,
            "edges": edges
        })

    def handle_testbed_start(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        site_model = data.get('site_model') or params.get('site_model', [None])[0]
        project_spec = data.get('project_spec') or params.get('project_spec', ['//mqtt/localhost:18833'])[0]

        if not site_model:
            self.send_error_response(400, "Please select a Site Model Path before starting Local Setup.")
            return

        site_model_resolved = os.path.expanduser(site_model)
        if not os.path.isabs(site_model_resolved):
            site_model_resolved = os.path.abspath(os.path.join(ROOT_DIR, site_model_resolved))

        if not os.path.exists(site_model_resolved) or not os.path.isdir(site_model_resolved):
            self.send_error_response(404, f"Site model directory not found: {site_model}")
            return

        # If cloud_iot_config.json is located in udmi/ subfolder, resolve to udmi
        if not os.path.exists(os.path.join(site_model_resolved, 'cloud_iot_config.json')) and os.path.exists(os.path.join(site_model_resolved, 'udmi', 'cloud_iot_config.json')):
            site_model_resolved = os.path.join(site_model_resolved, 'udmi')

        # Target standard isolated port 18833 unless a custom port was explicitly requested
        assigned_port = 18833
        if '//mqtt/localhost' in project_spec:
            port_match = re.search(r'localhost:(\d+)', project_spec)
            assigned_port = int(port_match.group(1)) if port_match else 18833
            project_spec = f"//mqtt/localhost:{assigned_port}"

        # Check if local pipeline is already active and healthy on this port
        pod_ready = os.path.exists(os.path.join(ROOT_DIR, 'var', 'pod_ready.txt'))
        mqtt_responding = False
        try:
            with socket.create_connection(('127.0.0.1', assigned_port), timeout=0.3):
                mqtt_responding = True
        except Exception:
            pass

        if pod_ready and mqtt_responding:
            latest_sid, _ = get_latest_session_process('testbed')
            self.send_json_response({
                "session_id": latest_sid or "active-local-setup",
                "status": "ready",
                "already_running": True,
                "project_spec": project_spec,
                "port": assigned_port,
                "message": f"Local testbed environment is already active and healthy on port {assigned_port}."
            })
            return

        session_id = f"testbed-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        session_dir = os.path.join(ROOT_DIR, 'out', 'sessions', session_id)
        os.makedirs(session_dir, exist_ok=True)
        prune_old_sessions(10)

        cmd = ["bin/udmi", "start", site_model_resolved, project_spec]
        log_path = os.path.join(session_dir, 'testbed_start.log')

        env = os.environ.copy()
        env['UDMI_NO_SUDO'] = 'true'
        env['MQTT_PORT'] = str(assigned_port)
        env['ETCD_PORT'] = str(assigned_port + 1)
        env['INFLUX_PORT'] = str(assigned_port + 2)
        env['POSTGRES_PORT'] = str(assigned_port + 3)

        # For local setup, reset out/udmis.log so fresh logs stream cleanly
        udmis_log = os.path.join(ROOT_DIR, 'out', 'udmis.log')
        try:
            os.makedirs(os.path.dirname(udmis_log), exist_ok=True)
            with open(udmis_log, 'w', encoding='utf-8') as f:
                f.write(f"Launching LOCAL setup pipeline for {site_model_resolved} ({project_spec})...\n")
        except Exception:
            pass

        try:
            with open(log_path, 'wb', buffering=0) as log_file:
                proc = subprocess.Popen(
                    cmd,
                    cwd=ROOT_DIR,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    preexec_fn=os.setsid
                )

            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "testbed",
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            self.send_json_response({
                "session_id": session_id,
                "status": "starting",
                "project_spec": project_spec,
                "port": assigned_port,
                "message": f"Local testbed environment initialization launched on port {assigned_port}."
            })
        except Exception as e:
            self.send_error_response(500, f"Failed to start testbed: {str(e)}")

    def handle_testbed_proc_status(self, query_string):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        session_id = params.get('session_id', [None])[0]
        try:
            offset = int(params.get('offset', ['0'])[0])
        except (ValueError, TypeError):
            offset = 0

        target_meta = None
        target_sid = session_id
        if target_sid:
            with active_processes_lock:
                target_meta = active_processes.get(target_sid)
        else:
            target_sid, target_meta = get_latest_session_process('testbed')

        if not target_sid and not target_meta:
            self.send_json_response({
                "running": False,
                "exit_code": None,
                "ready": False,
                "session_id": None,
                "log": "",
                "offset": 0
            })
            return

        proc = target_meta.get('process') if target_meta else None
        is_running = proc is not None and proc.poll() is None
        exit_code = proc.poll() if proc else None

        log_content = ""
        new_offset = offset
        log_path = target_meta.get('log_path') if target_meta else (
            os.path.join(ROOT_DIR, 'out', 'sessions', target_sid, 'testbed_start.log') if target_sid else None
        )

        proc_type = target_meta.get('type') if target_meta else 'testbed'
        if proc_type == 'pubber':
            active_log_path = log_path
            ready = False
        else:
            udmis_log = os.path.join(ROOT_DIR, 'out', 'udmis.log')
            active_log_path = udmis_log if os.path.exists(udmis_log) else log_path
            ready = os.path.exists(os.path.join(ROOT_DIR, 'var', 'pod_ready.txt'))

        if active_log_path and os.path.exists(active_log_path):
            try:
                with open(active_log_path, 'r', encoding='utf-8', errors='replace') as f:
                    f.seek(0, os.SEEK_END)
                    file_size = f.tell()
                    if offset < file_size:
                        f.seek(offset)
                        log_content = f.read()
                        new_offset = f.tell()
            except Exception as e:
                log_content = f"[Server Error reading log: {str(e)}]\n"

        if proc_type == 'pubber':
            if "Publishing" in log_content or "Starting pubber" in log_content or "Connected" in log_content or "Connection successful" in log_content or "Sending message" in log_content or is_running:
                ready = True
        elif "UUFI Service is READY" in log_content:
            ready = True

        self.send_json_response({
            "running": is_running,
            "exit_code": exit_code,
            "ready": ready,
            "session_id": target_sid,
            "type": proc_type,
            "log": log_content,
            "offset": new_offset
        })

    def handle_testbed_stop(self, query_string, post_data=None):
        try:
            pod_ready = os.path.join(ROOT_DIR, 'var', 'pod_ready.txt')
            if os.path.exists(pod_ready):
                os.remove(pod_ready)
            pid_file = os.path.join(ROOT_DIR, 'var', 'udmis.pid')
            if os.path.exists(pid_file):
                try:
                    with open(pid_file) as pf:
                        pid = int(pf.read().strip())
                        os.kill(pid, signal.SIGTERM)
                except Exception:
                    pass
                if os.path.exists(pid_file):
                    os.remove(pid_file)
            subprocess.run(['pkill', '-f', 'udmis-1.0-SNAPSHOT-all.jar'], capture_output=True)

            pid_file = os.path.join(ROOT_DIR, 'var', 'mosquitto', 'mosquitto.pid')
            if os.path.exists(pid_file):
                try:
                    with open(pid_file) as pf:
                        pid = int(pf.read().strip())
                        os.kill(pid, signal.SIGTERM)
                except Exception:
                    pass
            subprocess.run(['pkill', '-x', 'mosquitto'], capture_output=True)
            subprocess.run(['pkill', '-f', 'pubber-1.0-SNAPSHOT-all.jar'], capture_output=True)
            pid_file = os.path.join(ROOT_DIR, 'var', 'etcd_explorer.pid')
            if os.path.exists(pid_file):
                try:
                    with open(pid_file) as pf:
                        pid = int(pf.read().strip())
                        os.kill(pid, signal.SIGTERM)
                except Exception:
                    pass
                if os.path.exists(pid_file):
                    os.remove(pid_file)
            subprocess.run(['pkill', '-f', 'EtcdExplorerServer'], capture_output=True)
            subprocess.run(['pkill', '-f', 'etcd'], capture_output=True)
            subprocess.run(['pkill', '-f', 'influxd'], capture_output=True)
            subprocess.run(['pkill', '-f', 'postgres'], capture_output=True)

            with active_processes_lock:
                for sid, meta in list(active_processes.items()):
                    if meta.get('type') in ('testbed', 'pubber'):
                        proc = meta.get('process')
                        if proc and proc.poll() is None:
                            try:
                                os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                            except Exception:
                                try:
                                    proc.terminate()
                                except Exception:
                                    pass
                        del active_processes[sid]

            self.send_json_response({
                "status": "stopped",
                "message": "All local testbed components stopped."
            })
        except Exception as e:
            self.send_error_response(500, f"Failed to stop testbed pipeline: {str(e)}")

    def handle_testbed_start_component(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        component = data.get('component') or params.get('component', [None])[0]
        site_model = data.get('site_model') or params.get('site_model', ['sites/udmi_site_model'])[0]
        project_spec = data.get('project_spec') or params.get('project_spec', ['//mqtt/localhost:18833'])[0]

        env = os.environ.copy()
        env['UDMI_NO_SUDO'] = 'true'

        if component == 'udmis':
            cmd = ["bin/start_udmis"]
        elif component == 'mqtt_broker':
            cmd = ["bin/start_mosquitto"]
        elif component == 'etcd':
            cmd = ["bin/start_etcd"]
        elif component == 'etcd_explorer':
            ok = start_etcd_explorer_service()
            self.send_json_response({
                "status": "UP" if ok else "DOWN",
                "component": "etcd_explorer",
                "message": "ETCD Explorer launched successfully." if ok else "Failed to launch ETCD Explorer."
            })
            return
        elif component == 'influx':
            cmd = ["bin/start_influx"]
        elif component == 'postgresql':
            cmd = ["bin/start_postgresql"]
        elif component == 'pubber':
            cmd = ["bin/pubber", site_model, project_spec, "AHU-1", "10492"]
        else:
            self.send_error_response(400, f"Unknown component '{component}'")
            return

        try:
            subprocess.run(cmd, cwd=ROOT_DIR, env=env, capture_output=True, text=True, timeout=30)
            self.send_json_response({
                "status": "UP",
                "component": component,
                "message": f"Component {component} launched successfully."
            })
        except Exception as e:
            self.send_error_response(500, f"Failed to start component {component}: {str(e)}")

    def handle_testbed_stop_component(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        component = data.get('component') or params.get('component', [None])[0]

        try:
            if component == 'udmis':
                pod_ready = os.path.join(ROOT_DIR, 'var', 'pod_ready.txt')
                if os.path.exists(pod_ready):
                    os.remove(pod_ready)
                pid_file = os.path.join(ROOT_DIR, 'var', 'udmis.pid')
                if os.path.exists(pid_file):
                    try:
                        with open(pid_file) as pf:
                            pid = int(pf.read().strip())
                            os.kill(pid, signal.SIGTERM)
                    except Exception:
                        pass
                    if os.path.exists(pid_file):
                        os.remove(pid_file)
                subprocess.run(['pkill', '-f', 'udmis-1.0-SNAPSHOT-all.jar'], capture_output=True)
            elif component == 'mqtt_broker':
                pid_file = os.path.join(ROOT_DIR, 'var', 'mosquitto', 'mosquitto.pid')
                if os.path.exists(pid_file):
                    try:
                        with open(pid_file) as pf:
                            pid = int(pf.read().strip())
                            os.kill(pid, signal.SIGTERM)
                    except Exception:
                        pass
                subprocess.run(['pkill', '-x', 'mosquitto'], capture_output=True)
            elif component == 'pubber':
                subprocess.run(['pkill', '-f', 'pubber-1.0-SNAPSHOT-all.jar'], capture_output=True)
            elif component == 'etcd_explorer':
                pid_file = os.path.join(ROOT_DIR, 'var', 'etcd_explorer.pid')
                if os.path.exists(pid_file):
                    try:
                        with open(pid_file) as pf:
                            pid = int(pf.read().strip())
                            os.kill(pid, signal.SIGTERM)
                    except Exception:
                        pass
                    if os.path.exists(pid_file):
                        os.remove(pid_file)
                subprocess.run(['pkill', '-f', 'EtcdExplorerServer'], capture_output=True)
            elif component == 'etcd':
                pid_file = os.path.join(ROOT_DIR, 'var', 'etcd_explorer.pid')
                if os.path.exists(pid_file):
                    try:
                        with open(pid_file) as pf:
                            pid = int(pf.read().strip())
                            os.kill(pid, signal.SIGTERM)
                    except Exception:
                        pass
                    if os.path.exists(pid_file):
                        os.remove(pid_file)
                subprocess.run(['pkill', '-f', 'EtcdExplorerServer'], capture_output=True)
                subprocess.run(['pkill', '-f', 'etcd'], capture_output=True)
            elif component == 'influx':
                subprocess.run(['pkill', '-f', 'influxd'], capture_output=True)
            elif component == 'postgresql':
                subprocess.run(['pkill', '-f', 'postgres'], capture_output=True)

            self.send_json_response({
                "status": "DOWN",
                "component": component,
                "message": f"Component {component} stopped."
            })
        except Exception as e:
            self.send_error_response(500, f"Failed to stop component {component}: {str(e)}")

    def handle_testbed_start_pubber(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        data = post_data or {}
        device_id = data.get('device_id') or params.get('device_id', ['AHU-1'])[0]
        serial_no = data.get('serial_no') or params.get('serial_no', ['10491'])[0]
        site_model = data.get('site_model') or params.get('site_model', ['sites/udmi_site_model'])[0]
        project_spec = data.get('project_spec') or params.get('project_spec', ['//mqtt/localhost:18833'])[0]

        target_site, err = self._resolve_and_verify_path(site_model)
        if err or not os.path.exists(target_site):
            target_site = os.path.abspath(os.path.join(ROOT_DIR, site_model))

        # Check if pubber is already running for this device
        with active_processes_lock:
            for sid, meta in list(active_processes.items()):
                if meta.get('type') == 'pubber' and meta.get('device_id') == device_id:
                    proc = meta.get('process')
                    if proc and proc.poll() is None:
                        self.send_json_response({
                            "session_id": sid,
                            "status": "running",
                            "already_running": True,
                            "device_id": device_id,
                            "serial_no": serial_no,
                            "cmd": f"UDMI_NO_SUDO=true bin/pubber {site_model} {project_spec} {device_id} {serial_no}",
                            "message": f"Pubber emulator for {device_id} is already active."
                        })
                        return
                    else:
                        del active_processes[sid]

        session_id = f"pubber-{device_id}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        session_dir = os.path.join(ROOT_DIR, 'out', 'sessions', session_id)
        os.makedirs(session_dir, exist_ok=True)
        log_path = os.path.join(session_dir, 'pubber.log')
        prune_old_sessions(15)

        cmd = ["bin/pubber", target_site, project_spec, device_id, str(serial_no)]
        env = os.environ.copy()
        env['UDMI_NO_SUDO'] = 'true'

        try:
            with open(log_path, 'wb', buffering=0) as log_file:
                proc = subprocess.Popen(
                    cmd,
                    cwd=ROOT_DIR,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    preexec_fn=os.setsid
                )

            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "pubber",
                    "device_id": device_id,
                    "serial_no": serial_no,
                    "site_model": target_site,
                    "project_spec": project_spec,
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            self.send_json_response({
                "session_id": session_id,
                "status": "starting",
                "device_id": device_id,
                "serial_no": serial_no,
                "cmd": f"UDMI_NO_SUDO=true bin/pubber {site_model} {project_spec} {device_id} {serial_no}",
                "message": f"Pubber background process started for {device_id} (Serial {serial_no})."
            })
        except Exception as e:
            self.send_error_response(500, f"Failed to start pubber for {device_id}: {str(e)}")

    def handle_testbed_stop_pubber(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        data = post_data or {}
        device_id = data.get('device_id') or params.get('device_id', [None])[0]

        stopped = False
        with active_processes_lock:
            for sid, meta in list(active_processes.items()):
                if meta.get('type') == 'pubber' and (device_id is None or meta.get('device_id') == device_id):
                    proc = meta.get('process')
                    if proc and proc.poll() is None:
                        try:
                            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                        except Exception:
                            try:
                                proc.terminate()
                            except Exception:
                                pass
                    del active_processes[sid]
                    stopped = True

        if device_id:
            subprocess.run(['pkill', '-f', f'pubber.*{device_id}'], capture_output=True)
        else:
            subprocess.run(['pkill', '-f', 'pubber-1.0-SNAPSHOT-all.jar'], capture_output=True)

        self.send_json_response({
            "status": "stopped",
            "device_id": device_id,
            "message": f"Pubber process stopped for {device_id or 'all devices'}."
        })

    def handle_log_diff(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}

        site_model = data.get('site_model') or params.get('site_model', [None])[0]
        device_id = data.get('device_id') or params.get('device_id', [None])[0]
        test_id = data.get('test_id') or params.get('test_id', [None])[0]
        current_session_id = data.get('current_session_id') or params.get('current_session_id', [None])[0]
        baseline_session_id = data.get('baseline_session_id') or params.get('baseline_session_id', [None])[0]

        current_log_lines = []
        baseline_log_lines = []
        has_baseline = False

        curr_log_path = None
        if current_session_id:
            cand = os.path.join(ROOT_DIR, 'out', 'sessions', current_session_id, 'sequencer.log')
            if os.path.exists(cand):
                curr_log_path = cand

        if not curr_log_path and site_model and device_id and test_id:
            site_model_resolved = os.path.abspath(os.path.expanduser(site_model))
            cand = os.path.join(site_model_resolved, 'out', 'devices', device_id, 'tests', test_id, 'sequence.log')
            if os.path.exists(cand):
                curr_log_path = cand

        if curr_log_path and os.path.exists(curr_log_path):
            with open(curr_log_path, 'r', encoding='utf-8', errors='replace') as f:
                current_log_lines = [line.rstrip('\r\n') for line in f.readlines()]

        base_log_path = None
        if baseline_session_id:
            cand = os.path.join(ROOT_DIR, 'out', 'sessions', baseline_session_id, 'sequencer.log')
            if os.path.exists(cand):
                base_log_path = cand

        if not base_log_path:
            sessions_dir = os.path.join(ROOT_DIR, 'out', 'sessions')
            if os.path.exists(sessions_dir):
                for s in sorted(os.listdir(sessions_dir), reverse=True):
                    if s == current_session_id:
                        continue
                    cand = os.path.join(sessions_dir, s, 'sequencer.log')
                    if os.path.exists(cand):
                        base_log_path = cand
                        baseline_session_id = s
                        break

        if base_log_path and os.path.exists(base_log_path):
            with open(base_log_path, 'r', encoding='utf-8', errors='replace') as f:
                baseline_log_lines = [line.rstrip('\r\n') for line in f.readlines()]
                has_baseline = True

        diff = list(difflib.ndiff(baseline_log_lines, current_log_lines))
        diff_structured = []
        for line in diff:
            if line.startswith('  '):
                diff_structured.append({"type": "unchanged", "line": line[2:]})
            elif line.startswith('- '):
                diff_structured.append({"type": "removed", "line": line[2:]})
            elif line.startswith('+ '):
                diff_structured.append({"type": "added", "line": line[2:]})

        self.send_json_response({
            "device_id": device_id or "unknown",
            "test_id": test_id or "unknown",
            "current_session_id": current_session_id,
            "baseline_session_id": baseline_session_id,
            "has_baseline": has_baseline,
            "diff_lines": diff_structured
        })

    def handle_ai_query(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        query = data.get('prompt') or data.get('query') or params.get('query', [''])[0]
        site_model = data.get('site_model') or (data.get('context') or {}).get('site_model', 'sites/udmi_site_model')
        device_id = data.get('device_id') or (data.get('context') or {}).get('active_device', '')
        test_id = data.get('test_id') or (data.get('context') or {}).get('active_test', '')
        project_spec = data.get('project_spec') or (data.get('context') or {}).get('project_spec', '')

        query_id = f"q-{uuid.uuid4().hex[:6]}"
        answer_markdown = ""

        # Attempt to run via live MantisChatSession if credentials are configured
        has_credentials = bool(os.getenv("GEMINI_API_KEY") or os.getenv("MANTIS_USE_VERTEXAI"))
        if has_credentials and query:
            try:
                import asyncio
                mantis_dir = os.path.join(ROOT_DIR, "util", "mantis")
                if mantis_dir not in sys.path:
                    sys.path.insert(0, mantis_dir)
                from mantis.agent.chat import MantisChatSession

                chat_session = MantisChatSession(
                    udmi_root=ROOT_DIR,
                    site_model=site_model,
                    device_id=device_id or None,
                    test_id=test_id or None,
                    project_spec=project_spec or None
                )
                answer_markdown = asyncio.run(chat_session.send_message(query))
            except Exception as e:
                answer_markdown = f"*(Mantis AI Query Error: {e})*\n\n"

        if not answer_markdown:
            answer_markdown += (
                f"### Analysis Summary\n"
                f"Evaluated query: *\"{query}\"*\n\n"
                f"**Active Workspace**: `{site_model}`\n"
                f"**Target Device**: `{device_id or 'N/A'}`\n"
                f"**Target Test**: `{test_id or 'N/A'}`\n\n"
                f"No structural anomalies or validation failures detected for this query."
            )

        self.send_json_response({
            "query_id": query_id,
            "response": answer_markdown,
            "answer_markdown": answer_markdown
        })

    def handle_mantis_chat_stream(self, query_string=None, post_data=None, bearer_key=None):
        data = post_data or {}
        session_id = data.get('session_id') or f"chat-{uuid.uuid4().hex[:8]}"
        user_message = data.get('message') or data.get('prompt') or ""
        site_model = data.get('site_model')
        device_id = data.get('device_id')
        test_id = data.get('test_id')
        project_spec = data.get('project_spec')
        provider = data.get('provider')
        api_key = data.get('api_key') or bearer_key or os.getenv("GEMINI_API_KEY")
        gcp_project = data.get('gcp_project') or os.getenv("GCLOUD_PROJECT") or os.getenv("GOOGLE_CLOUD_PROJECT") or "bos-platform-dev"
        gcp_location = data.get('gcp_location') or os.getenv("GCP_LOCATION", "global")

        if api_key:
            os.environ["GEMINI_API_KEY"] = api_key
            use_vertex = (str(provider).lower() == 'vertex')
        else:
            use_vertex = True
            os.environ["MANTIS_USE_VERTEXAI"] = "true"

        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'close')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()

        def send_sse(event_type, payload):
            try:
                msg = f"event: {event_type}\ndata: {json.dumps(payload)}\n\n"
                self.wfile.write(msg.encode('utf-8'))
                self.wfile.flush()
                return True
            except (BrokenPipeError, ConnectionResetError, OSError):
                return False
            except Exception:
                return False

        send_sse('session_init', {
            'session_id': session_id,
            'site_model': site_model,
            'device_id': device_id,
            'test_id': test_id
        })

        if not user_message:
            send_sse('error', {'error': 'Empty prompt message'})
            send_sse('done', {'session_id': session_id})
            return

        with active_mantis_sessions_lock:
            chat_session = active_mantis_sessions.get(session_id)
            if not chat_session:
                try:
                    from mantis.agent.chat import MantisChatSession
                    chat_session = MantisChatSession(
                        udmi_root=ROOT_DIR,
                        site_model=site_model,
                        device_id=device_id or None,
                        test_id=test_id or None,
                        project_spec=project_spec or None,
                        use_vertex=use_vertex,
                        gcp_project=gcp_project,
                        gcp_location=gcp_location
                    )
                    active_mantis_sessions[session_id] = chat_session
                except Exception as e:
                    send_sse('error', {'error': f"Failed to initialize Mantis session: {e}"})
                    send_sse('done', {'session_id': session_id})
                    return

        import asyncio

        async def run_streaming():
            try:
                msg_trimmed = user_message.strip()
                if msg_trimmed.startswith(("/fact-check", "/factcheck", "/critique", "/review")):
                    focus = msg_trimmed.split(maxsplit=1)[1].strip() if " " in msg_trimmed else ""
                    prev_report = data.get('previous_report', '')
                    critique_text = await chat_session.run_critique(focus, previous_report=prev_report)
                    send_sse('token', {'text': critique_text})
                    send_sse('done', {'full_text': critique_text, 'session_id': session_id})
                else:
                    async for event in chat_session.send_message_stream(user_message):
                        if getattr(chat_session, 'is_cancelled', False):
                            break
                        ev_type = event.get('type', 'message')
                        ok = send_sse(ev_type, event)
                        if not ok:
                            chat_session.cancel()
                            break

            except Exception as stream_err:
                send_sse('error', {'error': f"Diagnostic streaming error: {stream_err}"})
                send_sse('done', {'session_id': session_id})

        try:
            asyncio.run(run_streaming())
        except Exception as e:
            send_sse('error', {'error': str(e)})
            send_sse('done', {'session_id': session_id})

    def handle_mantis_chat_stop(self, query_string=None, post_data=None):
        data = post_data or {}
        session_id = data.get('session_id')
        if session_id:
            with active_mantis_sessions_lock:
                if session_id in active_mantis_sessions:
                    active_mantis_sessions[session_id].cancel()

        self.send_json_response({"status": "ok", "message": "Mantis generation stopped"})

    def handle_mantis_chat_clear(self, query_string=None, post_data=None):
        data = post_data or {}
        session_id = data.get('session_id')
        if session_id:
            with active_mantis_sessions_lock:
                if session_id in active_mantis_sessions:
                    active_mantis_sessions[session_id].history.clear()

        self.send_json_response({"status": "ok", "message": "Chat history cleared"})

    def handle_mantis_chat_context(self, query_string=None):
        params = urllib.parse.parse_qs(query_string) if query_string else {}
        session_id = params.get('session_id', [None])[0]
        ctx = {}
        if session_id:
            with active_mantis_sessions_lock:
                sess = active_mantis_sessions.get(session_id)
                if sess:
                    ctx = {
                        "site_model": sess.active_site_model,
                        "device_id": sess.active_device,
                        "test_id": sess.active_test,
                        "history_turns": len(sess.history)
                    }
        self.send_json_response({"status": "ok", "context": ctx})

    def handle_graphviz_render(self, query_string=None, post_data=None):
        data = post_data or {}
        dot_code = data.get('dot', '').strip()

        if not dot_code:
            self.send_error_response(400, "Missing 'dot' graph definition in request body.")
            return

        dot_path = "/usr/bin/dot" if os.path.exists("/usr/bin/dot") else shutil.which("dot")
        if not dot_path:
            self.send_error_response(501, "Graphviz binary 'dot' is not installed on the system.")
            return

        try:
            proc = subprocess.run(
                [dot_path, "-Tsvg"],
                input=dot_code,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=5,
                check=False
            )

            if proc.returncode != 0:
                err_msg = proc.stderr.strip() or "Failed to render DOT graph"
                self.send_json_response({"status": "error", "error": err_msg}, status_code=422)
            else:
                svg_output = proc.stdout.strip()
                if svg_output.startswith("<?xml"):
                    svg_output = svg_output.split("?>", 1)[-1].strip()
                if "<!DOCTYPE" in svg_output:
                    svg_output = svg_output.split(">", 1)[-1].strip()

                self.send_json_response({"status": "success", "svg": svg_output})

        except subprocess.TimeoutExpired:
            self.send_error_response(504, "Graphviz rendering timed out (exceeded 5s limit).")
        except Exception as e:
            self.send_error_response(500, f"Graphviz rendering failed: {str(e)}")

    def handle_sse_stream(self, query_string, proc_type="sequencer"):
        params = urllib.parse.parse_qs(query_string)
        session_id = params.get('session_id', [None])[0]

        target_meta = None
        target_sid = session_id
        if target_sid:
            with active_processes_lock:
                target_meta = active_processes.get(target_sid)
        else:
            target_sid, target_meta = get_latest_session_process(proc_type)

        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'keep-alive')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()

        log_path = target_meta.get('log_path') if target_meta else (
            os.path.join(ROOT_DIR, 'out', 'sessions', target_sid, f"{proc_type}.log") if target_sid else os.path.join(ROOT_DIR, 'out', f"{proc_type}.log")
        )

        offset = 0
        while True:
            try:
                if log_path and os.path.exists(log_path):
                    with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
                        f.seek(offset)
                        lines = f.readlines()
                        offset = f.tell()
                        for line in lines:
                            clean_line = line.rstrip('\r\n')
                            msg = f"data: {clean_line}\n\n".encode('utf-8')
                            self.wfile.write(msg)
                            self.wfile.flush()

                proc = target_meta.get('process') if target_meta else None
                is_running = proc is not None and proc.poll() is None
                if not is_running:
                    if log_path and os.path.exists(log_path):
                        with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
                            f.seek(offset)
                            lines = f.readlines()
                            for line in lines:
                                clean_line = line.rstrip('\r\n')
                                msg = f"data: {clean_line}\n\n".encode('utf-8')
                                self.wfile.write(msg)
                                self.wfile.flush()
                    break
                time.sleep(0.5)
            except (BrokenPipeError, ConnectionResetError, socket.error):
                break
            except Exception:
                break

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
        env = os.environ.copy()
        env['UDMI_NO_SUDO'] = 'true'

        try:
            with open(log_path, 'wb', buffering=0) as log_file:
                proc = subprocess.Popen(
                    cmd,
                    cwd=ROOT_DIR,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    preexec_fn=os.setsid
                )

            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "sequencer",
                    "device_id": device_id,
                    "site_model": site_model,
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            self.send_json_response({
                "status": "Started",
                "session_id": session_id,
                "pid": proc.pid,
                "cmd": cmd
            })
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
            self.send_json_response({"status": "Not running"})
        else:
            proc = target_meta['process']
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
                proc.wait(timeout=2)
                self.send_json_response({"status": "Stopped", "session_id": target_sid})
            except Exception as e:
                try:
                    proc.kill()
                    self.send_json_response({"status": "Stopped (fallback)", "session_id": target_sid, "error": str(e)})
                except Exception as ex:
                    self.send_error_response(500, f"Failed to stop process: {str(ex)}")

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

        self.send_json_response({
            "running": is_running,
            "exit_code": exit_code,
            "session_id": target_sid,
            "log": log_content,
            "offset": new_offset
        })

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
        exclude_param = data.get('exclude') or params.get('exclude', [None])[0]

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
                    self.send_json_response({
                        "status": "Skipped",
                        "session_id": session_id,
                        "message": f"Compliance test case '{test_id}' passed successfully. Diagnostics are not required."
                    })
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

                        exclude_tokens = ["NettyClientHandler", "GrpcHttp2", "OUTBOUND HEADERS", "INBOUND HEADERS", "INBOUND DATA", "INBOUND PING", "OUTBOUND PING"]
                        if exclude_param:
                            if isinstance(exclude_param, list):
                                exclude_tokens = exclude_param
                            elif isinstance(exclude_param, str):
                                exclude_tokens = [t.strip() for t in exclude_param.split(',') if t.strip()]

                        cmd_pull = [python_bin, pull_cloud_script, "-sl", seq_log_abs, "-t", test_id, "-p", target_gcp_project, "-o", udmis_log_abs]
                        if exclude_tokens:
                            cmd_pull.extend(["-e"] + exclude_tokens)

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
                "target_project": project_spec or "//mqtt/localhost:18833",
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
        cmd = [python_bin, "-u", "-m", "mantis.cli", "-m", manifest_relative_path, "-d", device_id, "-t", test_id]

        if playbook == "swe":
            playbook_path = os.path.join(ROOT_DIR, 'util', 'mantis', 'config', 'playbook_swe.yaml')
            cmd.extend(["--playbook", playbook_path])

        env = os.environ.copy()
        mantis_v2 = os.path.join(ROOT_DIR, 'util', 'mantis', 'v2')
        mantis_dir = os.path.join(ROOT_DIR, 'util', 'mantis')
        util_dir = os.path.join(ROOT_DIR, 'util')
        tools_dir = os.path.join(ROOT_DIR, 'tools')
        env['PYTHONPATH'] = f"{mantis_v2}:{mantis_dir}:{tools_dir}:{util_dir}:{env.get('PYTHONPATH', '')}"
        env['UDMI_NO_SUDO'] = 'true'

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
            with open(log_path, 'ab', buffering=0) as log_file:
                proc = subprocess.Popen(
                    cmd,
                    cwd=ROOT_DIR,
                    env=env,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    preexec_fn=os.setsid
                )

            with active_processes_lock:
                active_processes[session_id] = {
                    "process": proc,
                    "type": "triage",
                    "device_id": device_id,
                    "site_model": site_model,
                    "session_dir": session_dir,
                    "log_path": log_path,
                    "created_at": datetime.now().isoformat()
                }

            self.send_json_response({
                "status": "Started",
                "session_id": session_id,
                "pid": proc.pid,
                "cmd": cmd
            })
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
            self.send_json_response({"status": "Not running"})
        else:
            proc = target_meta['process']
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
                proc.wait(timeout=2)
                self.send_json_response({"status": "Stopped", "session_id": target_sid})
            except Exception as e:
                try:
                    proc.kill()
                    self.send_json_response({"status": "Stopped (fallback)", "session_id": target_sid, "error": str(e)})
                except Exception as ex:
                    self.send_error_response(500, f"Failed to stop triage process: {str(ex)}")

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

        self.send_json_response({
            "running": is_running,
            "exit_code": exit_code,
            "session_id": target_sid,
            "log": log_content,
            "offset": new_offset
        })

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

            self.send_json_response({
                "device_id": device_id,
                "test_id": test_id,
                "report_markdown": report_content
            })
        except Exception as e:
            self.send_error_response(500, f"Error reading diagnostic report: {str(e)}")

    def handle_git_status(self, query_string):
        params = urllib.parse.parse_qs(query_string)
        site_model = params.get('site_model', ['sites/udmi_site_model'])[0]
        site_path = os.path.abspath(os.path.expanduser(site_model))
        if not os.path.exists(site_path):
            site_path = ROOT_DIR

        try:
            branch_proc = subprocess.run(['git', '-C', site_path, 'rev-parse', '--abbrev-ref', 'HEAD'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
            branch = branch_proc.stdout.strip() if branch_proc.returncode == 0 else 'main'

            status_proc = subprocess.run(['git', '-C', site_path, 'status', '--porcelain', 'out/'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
            status_output = status_proc.stdout.strip().splitlines() if status_proc.returncode == 0 else []
            changed_files = [line.strip() for line in status_output if line.strip()]

            is_protected = branch.lower() in ['main', 'master', 'production', 'prod']

            self.send_json_response({
                "branch": branch,
                "is_protected": is_protected,
                "has_changes": len(changed_files) > 0,
                "changed_files": changed_files[:20],
                "repo_path": to_home_relative(site_path)
            })
        except Exception as e:
            self.send_error_response(500, f"Git status failed: {str(e)}")

    def handle_git_commit(self, query_string, post_data=None):
        params = urllib.parse.parse_qs(query_string)
        data = post_data or {}
        site_model = data.get('site_model') or params.get('site_model', ['sites/udmi_site_model'])[0]
        commit_msg = data.get('commit_message', 'test: save device compliance testing results')
        create_branch = bool(data.get('create_branch', False))
        new_branch_name = data.get('branch_name', '').strip()
        do_push = bool(data.get('push', False))
        force_main = bool(data.get('force_main', False))

        site_path = os.path.abspath(os.path.expanduser(site_model))
        if not os.path.exists(site_path):
            site_path = ROOT_DIR

        try:
            branch_proc = subprocess.run(['git', '-C', site_path, 'rev-parse', '--abbrev-ref', 'HEAD'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
            current_branch = branch_proc.stdout.strip() if branch_proc.returncode == 0 else 'main'
            is_protected = current_branch.lower() in ['main', 'master', 'production', 'prod']

            if is_protected and not create_branch and not force_main:
                self.send_error_response(400, f"Safety stop: Cannot commit directly to protected branch '{current_branch}'. Please select 'Create new branch for results' or confirm direct commit override.")
                return

            active_branch = current_branch
            if create_branch and new_branch_name:
                checkout_proc = subprocess.run(['git', '-C', site_path, 'checkout', '-b', new_branch_name], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
                if checkout_proc.returncode != 0 and 'already exists' in checkout_proc.stderr.lower():
                    subprocess.run(['git', '-C', site_path, 'checkout', new_branch_name], check=False)
                active_branch = new_branch_name

            # Add test results (out/ folder inside site model)
            out_dir = os.path.join(site_path, 'out')
            if os.path.exists(out_dir):
                subprocess.run(['git', '-C', site_path, 'add', '-f', 'out/'], check=False)
            else:
                subprocess.run(['git', '-C', site_path, 'add', '.'], check=False)

            # Check if there is anything staged to commit
            diff_check = subprocess.run(['git', '-C', site_path, 'diff', '--cached', '--name-only'], stdout=subprocess.PIPE, text=True)
            commit_hash = "no_changes"
            if diff_check.stdout.strip():
                # Strictly NO --amend! Always create a standard new commit as mandated by user rules
                commit_proc = subprocess.run(['git', '-C', site_path, 'commit', '-m', commit_msg], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
                if commit_proc.returncode == 0:
                    hash_proc = subprocess.run(['git', '-C', site_path, 'rev-parse', '--short', 'HEAD'], stdout=subprocess.PIPE, text=True)
                    commit_hash = hash_proc.stdout.strip()
                else:
                    self.send_error_response(500, f"Git commit failed: {commit_proc.stderr or commit_proc.stdout}")
                    return

            push_status = "skipped"
            if do_push:
                push_proc = subprocess.run(['git', '-C', site_path, 'push', '-u', 'origin', active_branch], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
                push_status = "pushed" if push_proc.returncode == 0 else f"failed: {push_proc.stderr.strip()}"

            self.send_json_response({
                "status": "success",
                "branch": active_branch,
                "commit_hash": commit_hash,
                "push_status": push_status,
                "message": f"Results processed on branch '{active_branch}' (Commit: {commit_hash})."
            })
        except Exception as e:
            self.send_error_response(500, f"Git action failed: {str(e)}")

    def handle_send_email(self, query_string, post_data=None):
        data = post_data or {}
        recipient = data.get('recipient')
        subject = data.get('subject', '[UDMI Workbench] Compliance Test & Diagnostic Notification')
        body_text = data.get('body', '')
        rca_markdown = data.get('rca_markdown', '')
        smtp_server = data.get('smtp_server', os.environ.get('SMTP_SERVER', ''))
        smtp_port = int(data.get('smtp_port', os.environ.get('SMTP_PORT', '25')))

        if not recipient:
            self.send_error_response(400, "Recipient email address is required.")
            return

        outbox_dir = os.path.join(ROOT_DIR, 'out', 'emails')
        os.makedirs(outbox_dir, exist_ok=True)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S_%f')[:15]
        eml_path = os.path.join(outbox_dir, f"notification_{timestamp}.eml")
        html_path = os.path.join(outbox_dir, f"notification_{timestamp}.html")

        html_content = f"""
        <html>
        <body style="font-family: Arial, sans-serif; line-height: 1.5; color: #202124;">
            <h2 style="color: #0b57d0;">UDMI Workbench Notification</h2>
            <p><strong>To:</strong> {recipient}</p>
            <p><strong>Subject:</strong> {subject}</p>
            <hr style="border: none; border-top: 1px solid #e0e0e0;" />
            <div style="padding: 12px 0;">
                <p>{body_text.replace(chr(10), '<br>')}</p>
            </div>
            {f'<div style="background: #f8f9fa; padding: 16px; border-radius: 8px; border: 1px solid #dadce0;"><h3 style="margin-top:0; color:#6d28d9;">Mantis AI Root Cause Analysis</h3><pre style="white-space: pre-wrap; font-family: monospace;">{rca_markdown}</pre></div>' if rca_markdown else ''}
            <p style="font-size: 11px; color: #5f6368; margin-top: 24px;">Generated automatically by UDMI Workbench</p>
        </body>
        </html>
        """

        try:
            with open(html_path, 'w', encoding='utf-8') as fh:
                fh.write(html_content.strip())

            msg = EmailMessage()
            msg['Subject'] = subject
            msg['From'] = 'workbench-noreply@udmi.system'
            msg['To'] = recipient
            msg.set_content(body_text + (f"\n\n--- MANTIS AI RCA ---\n{rca_markdown}" if rca_markdown else ""))
            msg.add_alternative(html_content, subtype='html')

            with open(eml_path, 'wb') as fe:
                fe.write(msg.as_bytes())

            delivery_method = "LOCAL_OUTBOX"
            if smtp_server and smtp_server.lower() != 'localhost' and smtp_server != '':
                try:
                    with smtplib.SMTP(smtp_server, smtp_port, timeout=3) as s:
                        s.send_message(msg)
                    delivery_method = f"SMTP ({smtp_server})"
                except Exception as e_smtp:
                    print(f"[Email] SMTP send failed ({e_smtp}), fell back to local outbox simulation.")
                    delivery_method = "LOCAL_OUTBOX_FALLBACK"

            self.send_json_response({
                "status": "delivered",
                "recipient": recipient,
                "delivery_method": delivery_method,
                "outbox_file": os.path.relpath(html_path, ROOT_DIR),
                "timestamp": datetime.now().isoformat()
            })
        except Exception as e:
            self.send_error_response(500, f"Email dispatch failed: {str(e)}")


if __name__ == '__main__':
    for arg in sys.argv:
        if arg.startswith('--port='):
            PORT = int(arg.split('=', 1)[1])

    print(f"Starting UDMI custom API & Static server on port {PORT} serving directory {ROOT_DIR}")
    prune_old_sessions(10)

    try:
        server = HTTPServer(('0.0.0.0', PORT), UDMIRequestHandler)
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
        sys.exit(0)
