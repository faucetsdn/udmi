#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import time
import zipfile
import urllib.request
import urllib.error
from datetime import datetime

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

class Tee:
    """Dual-output stream to print to stdout/stderr and write persistently to a log file."""
    def __init__(self, original_stream, filepath):
        self.stream = original_stream
        self.file = open(filepath, 'a', encoding='utf-8')

    def write(self, data):
        self.stream.write(data)
        self.file.write(data)
        self.file.flush()

    def flush(self):
        self.stream.flush()
        self.file.flush()

class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Redirection handler that strips the Authorization header when redirecting to third-party domains."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        # Resolve hosts
        original_host = req.host
        new_host = new_req.host
        if original_host != new_host:
            # Remove Authorization header to prevent AWS S3 401 signature exceptions
            if "Authorization" in new_req.headers:
                del new_req.headers["Authorization"]
        return new_req

def run_command(args, cwd=UDMI_ROOT):
    """Helper to execute shell commands and handle failures cleanly."""
    print(f"Executing: {' '.join(args)}")
    import subprocess
    result = subprocess.run(args, cwd=cwd, stdout=sys.stdout, stderr=sys.stderr)
    return result.returncode

def clean_pubber_processes():
    """Kill any lingering pubber Java processes to guarantee environment isolation."""
    print("Cleaning up lingering pubber processes...")
    try:
        import subprocess
        ps_output = subprocess.check_output(["ps", "ax"], text=True)
        pids_to_kill = []
        for line in ps_output.splitlines():
            if "pubber" in line and "java" in line:
                pid = line.strip().split()[0]
                pids_to_kill.append(pid)
                
        if pids_to_kill:
            print(f"Killing lingering pubber PIDs: {', '.join(pids_to_kill)}")
            for pid in pids_to_kill:
                subprocess.run(["kill", "-9", pid], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as e:
        print(f"Warning: failed to clean pubber processes: {e}", file=sys.stderr)

def discover_git_details():
    """Discovers the git owner and repository name from git remote."""
    try:
        import subprocess
        out = subprocess.check_output(["git", "remote", "-v"], cwd=UDMI_ROOT, text=True)
        for line in out.splitlines():
            if line.startswith("origin") and "(fetch)" in line:
                parts = line.split()
                url = parts[1]
                if "github.com" in url:
                    if url.startswith("git@"):
                        path = url.split("github.com:")[1]
                    else:
                        path = url.split("github.com/")[1]
                    path = path.replace(".git", "")
                    owner, repo = path.split("/")
                    return owner.strip(), repo.strip()
    except Exception as e:
        print(f"Warning: Failed to parse git details: {e}", file=sys.stderr)
    return None, None

def discover_branch():
    """Discovers the current active branch name."""
    try:
        import subprocess
        out = subprocess.check_output(["git", "branch", "--show-current"], cwd=UDMI_ROOT, text=True)
        return out.strip()
    except Exception as e:
        print(f"Warning: Failed to parse git branch: {e}", file=sys.stderr)
    return "main"

class GitHubClient:
    def __init__(self, owner, repo, token):
        self.owner = owner
        self.repo = repo
        self.token = token
        self.base_url = "https://api.github.com"
        self.headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Mantis-Capture/1.0"
        }

    def _request(self, method, path, data=None):
        url = f"{self.base_url}{path}"
        req_data = json.dumps(data).encode('utf-8') if data else None
        
        req = urllib.request.Request(url, data=req_data, headers=self.headers, method=method)
        try:
            with urllib.request.urlopen(req) as response:
                if response.status == 204:
                    return None
                res_body = response.read()
                return json.loads(res_body.decode('utf-8'))
        except urllib.error.HTTPError as e:
            err_msg = e.read().decode('utf-8')
            print(f"HTTP Error {e.code} on {method} {path}: {err_msg}", file=sys.stderr)
            raise e
        except Exception as e:
            print(f"Network or parsing error: {e}", file=sys.stderr)
            raise e

    def get_latest_run_id(self, branch):
        """Fetches the ID of the absolute latest workflow run to serve as the baseline anchor."""
        path = f"/repos/{self.owner}/{self.repo}/actions/runs?event=workflow_dispatch&branch={branch}&per_page=1"
        res = self._request("GET", path)
        if res and res.get("workflow_runs"):
            return res["workflow_runs"][0]["id"]
        return 0

    def trigger_workflow(self, branch, target_project):
        """Triggers the manual testing.yml workflow via dispatch."""
        path = f"/repos/{self.owner}/{self.repo}/actions/workflows/testing.yml/dispatches"
        payload = {
            "ref": branch,
            "inputs": {
                "target_project": target_project
            }
        }
        self._request("POST", path, payload)

    def get_new_runs(self, branch, anchor_run_id, expected_count):
        """Polls until at least expected_count runs greater than anchor_run_id are discovered."""
        path = f"/repos/{self.owner}/{self.repo}/actions/runs?event=workflow_dispatch&branch={branch}&per_page=30"
        
        # Wait up to 30s for GitHub to register dispatch runs
        for _ in range(6):
            res = self._request("GET", path)
            new_runs = []
            if res and res.get("workflow_runs"):
                for run in res["workflow_runs"]:
                    if run["id"] > anchor_run_id:
                        new_runs.append(run["id"])
            
            if len(new_runs) >= expected_count:
                return sorted(new_runs)
            time.sleep(5)
            
        # Return whatever we resolved
        return sorted(new_runs)

    def get_run_details(self, run_id):
        path = f"/repos/{self.owner}/{self.repo}/actions/runs/{run_id}"
        return self._request("GET", path)

    def get_run_artifact_url(self, run_id):
        """Finds the support artifact ID for a specific run and returns its download path."""
        path = f"/repos/{self.owner}/{self.repo}/actions/runs/{run_id}/artifacts"
        res = self._request("GET", path)
        if res and res.get("artifacts"):
            for art in res["artifacts"]:
                if art["name"].startswith("udmi-support_") and not art.get("expired", False):
                    return art["id"], f"/repos/{self.owner}/{self.repo}/actions/artifacts/{art['id']}/zip"
        return None, None

    def download_artifact(self, download_path, save_filepath):
        """Downloads the artifact zip directly to disk, stripping authorization headers on redirects."""
        url = f"{self.base_url}{download_path}"
        req = urllib.request.Request(url, headers=self.headers)
        try:
            # Configure safe redirect handler opener to prevent authentication failures from AWS S3
            opener = urllib.request.build_opener(SafeRedirectHandler())
            with opener.open(req) as response:
                with open(save_filepath, 'wb') as f:
                    f.write(response.read())
            return True
        except Exception as e:
            print(f"Error downloading artifact from {url}: {e}", file=sys.stderr)
            return False

def main():
    parser = argparse.ArgumentParser(
        description="Mantis Capture - Automated Triggering and Bundling of GitHub CI or Local Test Sweeps"
    )
    # Common Arguments
    parser.add_argument("--target", default="//mqtt/localhost", help="Target project specification (default: //mqtt/localhost)")
    parser.add_argument("--iterations", type=int, help="Number of loops locally (default: 10) or parallel dispatches on GitHub (default: 3)")
    parser.add_argument("--output-dir", help="Directory to save resulting zip bundles")
    parser.add_argument("--verbose", action="store_true", help="Monitor live logs directly in your terminal foreground (configured via wrapper)")
    
    # Local-Specific Arguments
    parser.add_argument("--local", action="store_true", help="Execute loops locally in the sandbox instead of triggering GitHub CI")
    parser.add_argument("--suite", choices=["sequencer", "itemized", "both"], default="both", help="Test suite to run locally (default: both)")
    parser.add_argument("--tests", help="Comma-separated list of selective sequencer tests to run locally (e.g. valid_serial_no)")

    args = parser.parse_args()

    # Resolve output directory
    clean_target = args.target.replace("/", "_").replace("+", "_").strip("_")
    if not args.output_dir:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_dir = os.path.join(UDMI_ROOT, "out/mantis/test_bundles", f"{clean_target}_{timestamp}")
    else:
        output_dir = os.path.abspath(args.output_dir)

    os.makedirs(output_dir, exist_ok=True)

    log_filepath = os.path.join(output_dir, "capture.log")
    sys.stdout = Tee(sys.stdout, log_filepath)
    sys.stderr = Tee(sys.stderr, log_filepath)

    # =============================================================
    # TARGET EXECUTION MODULE
    # =============================================================

    # ----------------------------------------
    # MODE 1: LOCAL SANDBOX EXECUTION
    # ----------------------------------------
    if args.local:
        iterations = args.iterations if args.iterations is not None else 10
        
        print(f"=============================================================")
        print(f"🚀 Mantis Local Capture: Triggering Local Execution Loops")
        print(f"=============================================================")
        print(f"Target      : {args.target}")
        print(f"Iterations  : {iterations}")
        print(f"Suite       : {args.suite}")
        print(f"Output Folder: {output_dir}")
        print(f"=============================================================")

        # 1. Validate local mosquitto broker
        if "localhost" in args.target:
            print("Checking local broker port 1883...")
            import socket
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(1)
            result = sock.connect_ex(('127.0.0.1', 1883))
            sock.close()
            if result != 0:
                print("\n⚠️ Warning: Local mosquitto port 1883 is not open.")
                print("Please run local services first before starting the local loop:")
                print(f"  bin/start_local sites/udmi_site_model {args.target}\n", file=sys.stderr)
                sys.exit(1)

        # 2. Prebuild components
        print("Prebuilding pubber and validator once...")
        run_command(["pubber/bin/build"])
        run_command(["validator/bin/build"])

        specific_tests = args.tests.split(",") if args.tests else []

        seq_out = os.path.join(UDMI_ROOT, "out/sequencer.out")
        item_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")

        # 3. Loop execution
        for i in range(1, iterations + 1):
            print(f"\n=============================================================")
            print(f"🚀 Starting Local Iteration {i} of {iterations} at {datetime.now().strftime('%H:%M:%S')}")
            print(f"=============================================================")
            
            clean_pubber_processes()

            # Execute suites
            if args.suite in ["sequencer", "both"]:
                print("\n--- Executing Sequencer Suite ---")
                run_command(["bin/test_sequencer", "clean", "nocheck", args.target] + specific_tests)
                run_command(["bin/test_sequencer", "alpha", "nocheck", args.target] + specific_tests)

            if args.suite in ["itemized", "both"]:
                print("\n--- Executing Itemized Suite ---")
                run_command(["bin/test_itemized", args.target])

            # 4. ZIP raw results into standard udmi-support_run_{idx}.zip
            zip_filename = f"udmi-support_run_{i}.zip"
            zip_filepath = os.path.join(output_dir, zip_filename)
            
            print(f"\nPackaging iteration {i} outcomes into support archive {zip_filename}...")
            with zipfile.ZipFile(zip_filepath, 'w', zipfile.ZIP_DEFLATED) as zip_file:
                if os.path.exists(seq_out):
                    zip_file.write(seq_out, arcname="sequencer.out")
                if os.path.exists(item_out):
                    zip_file.write(item_out, arcname="test_itemized.out")
            
            print(f"Saved local support package: {zip_filepath}")
            clean_pubber_processes()

        print(f"\n=============================================================")
        print(f"🦗 Mantis Local Capture completed successfully!")
        print(f"Consolidated zip packages saved to: {output_dir}")
        print(f"=============================================================")
        print(f"\nTo measure flakiness / stability, execute:")
        print(f"  mantis/bin/grasp --target {args.target} --phase before --bundles-dir {output_dir}\n")

    # ----------------------------------------
    # MODE 2: GITHUB ACTIONS CI DISPATCH
    # ----------------------------------------
    else:
        iterations = args.iterations if args.iterations is not None else 3
        
        # Get GitHub Token
        token = os.getenv("GITHUB_TOKEN")
        if not token:
            print("Error: The environment variable 'GITHUB_TOKEN' is not set.", file=sys.stderr)
            print("Please set your GitHub Personal Access Token (PAT) before executing the hunter.", file=sys.stderr)
            print("Example: export GITHUB_TOKEN=\"your_pat_here\"", file=sys.stderr)
            sys.exit(1)

        # Discover Git details
        owner, repo = discover_git_details()
        branch = discover_branch()

        if not owner or not repo:
            print("Error: Could not discover GitHub repository details.", file=sys.stderr)
            sys.exit(1)

        print(f"=============================================================")
        print(f"🦗 Mantis GitHub Capture: Tracking Bugs on CI")
        print(f"=============================================================")
        print(f"Repo Target : {owner}/{repo} (branch: {branch})")
        print(f"CI Target   : {args.target}")
        print(f"Iterations  : {iterations}")
        print(f"Output Folder: {output_dir}")
        print(f"=============================================================")

        client = GitHubClient(owner=owner, repo=repo, token=token)

        # Step 1: Baseline run
        print("Locating the latest baseline run ID on GitHub...")
        anchor_id = client.get_latest_run_id(branch)
        print(f"Baseline anchor run ID: {anchor_id}")

        # Step 2: Dispatches
        print(f"\nTriggering {iterations} parallel workflow dispatch requests...")
        for idx in range(1, iterations + 1):
            try:
                client.trigger_workflow(branch, args.target)
                print(f"  -> Triggered run dispatch {idx} of {iterations}")
                time.sleep(1.5)
            except Exception:
                print(f"Failed to trigger run dispatch {idx}, aborting.", file=sys.stderr)
                sys.exit(1)

        # Step 3: Resolve IDs
        print("\nResolving newly created workflow run IDs...")
        new_run_ids = client.get_new_runs(branch, anchor_id, iterations)
        
        if len(new_run_ids) < iterations:
            print(f"Warning: Only resolved {len(new_run_ids)} out of {iterations} runs. Polling remainder.", file=sys.stderr)
        else:
            print(f"Successfully resolved run IDs: {', '.join(map(str, new_run_ids))}")

        # Step 4: Polling loop
        print("\nActive polling starting. Waiting for CI executions to finish...")
        completed_runs = set()
        downloaded_runs = set()

        while len(completed_runs) < len(new_run_ids):
            queued_count = 0
            in_progress_count = 0
            success_count = 0
            failed_count = 0
            cancelled_count = 0

            for run_id in new_run_ids:
                try:
                    details = client.get_run_details(run_id)
                    status = details.get("status")
                    conclusion = details.get("conclusion")

                    if status == "completed":
                        completed_runs.add(run_id)
                        if conclusion == "success":
                            success_count += 1
                        elif conclusion == "failure":
                            failed_count += 1
                        else:
                            cancelled_count += 1
                    elif status == "in_progress":
                        in_progress_count += 1
                    else:
                        queued_count += 1
                except Exception:
                    queued_count += 1

            print(f"  [{datetime.now().strftime('%H:%M:%S')}] Queued: {queued_count} | In-Progress: {in_progress_count} | Success: {success_count} | Failed: {failed_count} | Cancelled: {cancelled_count}", end="\r")
            
            if len(completed_runs) == len(new_run_ids):
                print()
                break
            time.sleep(25)

        # Step 5: Download artifacts
        print("\nCI runs completed. Commencing artifact retrieval...")
        for run_id in sorted(new_run_ids):
            print(f"Checking support package for Run {run_id}...")
            art_id = None
            art_path = None
            for _ in range(5):
                art_id, art_path = client.get_run_artifact_url(run_id)
                if art_path:
                    break
                time.sleep(3)

            if art_path:
                filename = f"udmi-support_{run_id}.zip"
                filepath = os.path.join(output_dir, filename)
                print(f"  Downloading support bundle {filename}...")
                ok = client.download_artifact(art_path, filepath)
                if ok:
                    downloaded_runs.add(run_id)
            else:
                print(f"  ⚠️ Warning: No support artifact found or expired for Completed Run {run_id}.", file=sys.stderr)

        # Summary
        print(f"\n=============================================================")
        print(f"🦗 GitHub Capture completed successfully!")
        print(f"Successfully downloaded: {len(downloaded_runs)} / {len(new_run_ids)} bundles.")
        print(f"Location: {output_dir}")
        print(f"=============================================================")
        print(f"\nTo measure stability / flakiness of these runs, execute:")
        print(f"  mantis/bin/grasp --target {args.target} --phase before --bundles-dir {output_dir}\n")

if __name__ == "__main__":
    main()
