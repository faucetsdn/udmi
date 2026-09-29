"""Commit sequencer results to version control (Layer 4).

After a sequencer run the lab needs the results
committed for historical tracking, with a message identifying the devices, the
fact that it was a sequencer run, and that it originated from Workbench.

Design decisions:
  * Commits are ONLY ever made in response to an explicit user action. There is
    no automatic or implicit commit path.
  * The scope is the SITE MODEL DIRECTORY, as a single pathspec relative to the
    repository root. It is not the repository: a lab repository may hold more
    than one site model, or hold the site model in a subdirectory (the staging
    lab keeps it at `<repo>/udmi`), so `git add .` would sweep in unrelated
    work. It is also no longer one device's results directory: a sequencer run
    writes `out/devices/<id>/**`, `out/sequencer_<id>.json` AND
    `devices/<id>/out/**`, and an earlier per-device scope committed only the
    first of those, leaving the rest of the run uncommitted.
  * The changed files are attributed back to devices so the operator is told
    which devices' recorded results this commit rewrites before they confirm.
  * Every precondition failure (not a repo, path ignored, nothing to commit,
    identity unset) is reported explicitly rather than worked around.

Prohibition on committing to the UDMI checkout:
  The Workbench must never commit to, switch branches in, or push the UDMI
  repository itself. When the site model resolves to a git repository whose
  root is the UDMI checkout, the whole feature is hard-blocked: `preview`
  reports `repo_is_udmi: True` with `committable: False`, and `commit` raises
  `CommitError` before any git write is attempted.
"""

from datetime import datetime, timezone
import os
import subprocess
from typing import Any, Dict, List, Optional, Tuple

from workbench.server.logger import SERVER_LOGGER

GIT_TIMEOUT_SECONDS = 30

# How many device names the generated commit message spells out before it
# switches to a remainder count. A site-wide run can touch every device in the
# model (77 in the staging lab), and a subject/body listing all of them is
# unreadable in `git log --oneline` and in review tooling. The true count is
# always stated in the subject, so truncation never hides the scale.
MESSAGE_DEVICE_LIMIT = 12


class CommitError(Exception):
    """Raised when results cannot be committed. Carries an actionable message."""


def _git(repo: str, *args: str) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(
            ["git", "-C", repo, *args],
            capture_output=True,
            text=True,
            timeout=GIT_TIMEOUT_SECONDS,
        )
    except FileNotFoundError as exc:
        raise CommitError("git executable not found on PATH.") from exc
    except subprocess.TimeoutExpired as exc:
        raise CommitError(f"git {' '.join(args)} timed out after {GIT_TIMEOUT_SECONDS}s.") from exc


def _repo_root(path: str) -> Optional[str]:
    """Returns the git repository root containing `path`, or None if untracked."""
    result = _git(path, "rev-parse", "--show-toplevel")
    if result.returncode != 0:
        return None
    return os.path.realpath(result.stdout.strip())


def _current_branch(repo: str) -> Optional[str]:
    """Returns the checked-out branch name, or None when HEAD is detached."""
    result = _git(repo, "symbolic-ref", "--quiet", "--short", "HEAD")
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def _branches(repo: str) -> List[str]:
    result = _git(repo, "for-each-ref", "--format=%(refname:short)", "refs/heads")
    if result.returncode != 0:
        return []
    return sorted(line.strip() for line in result.stdout.splitlines() if line.strip())


def _remotes(repo: str) -> List[str]:
    result = _git(repo, "remote")
    if result.returncode != 0:
        return []
    return sorted(line.strip() for line in result.stdout.splitlines() if line.strip())


def _site_label(site_model: str) -> str:
    """The human name of a site model, derived from its path alone.

    A site model kept at `<repo>/udmi` would otherwise be called "udmi" in every
    commit subject, which identifies nothing; the enclosing directory carries
    the lab's name (e.g. ZZ-TEST-SITE). Derived from the string rather than from
    cloud_iot_config.json so message building stays pure and deterministic.
    """
    normalised = os.path.normpath(site_model).rstrip(os.sep)
    base = os.path.basename(normalised)
    if base == "udmi":
        parent = os.path.basename(os.path.dirname(normalised))
        return parent or base
    return base or normalised


def device_for_path(relative_path: str) -> Optional[str]:
    """Maps a site-model-relative path to the device whose results it holds.

    A single sequencer run writes a device's artefacts to three unrelated
    places, so all three are recognised:
      * `out/devices/<id>/...`   - tests, results.md, RESULT.log
      * `out/sequencer_<id>.json` - the machine-readable run record
      * `devices/<id>/...`        - generated_config.json, metadata_norm.json,
                                    errors.map written back into the model

    Returns None for anything else (out/registration_summary.csv,
    cloud_iot_config.json): those are real changes but belong to the site, not
    to a device, and are reported separately rather than being attached to an
    arbitrary device.
    """
    parts = relative_path.split("/")
    if len(parts) >= 4 and parts[0] == "out" and parts[1] == "devices" and parts[2]:
        return parts[2]
    if len(parts) == 2 and parts[0] == "out":
        name = parts[1]
        if name.startswith("sequencer_") and name.endswith(".json"):
            return name[len("sequencer_"):-len(".json")] or None
    if len(parts) >= 3 and parts[0] == "devices" and parts[1]:
        return parts[1]
    return None


def _site_relative(site_pathspec: str, changes: List[str]) -> List[str]:
    """Re-expresses repo-relative git paths against the site model directory.

    The site model can be a subdirectory of its repository (the staging lab
    keeps it at `<repo>/udmi`), so git's own paths carry a prefix the operator
    never chose and would have to strip by eye. Only `path` - the pathspec git
    is actually given - stays repo-relative; every file list in the payload is
    relative to the site model.
    """
    if site_pathspec == ".":
        return list(changes)
    prefix = f"{site_pathspec}/"
    relative = []
    for path in changes:
        if not path.startswith(prefix):
            raise CommitError(
                f"git reported a change at '{path}' that is outside the committed "
                f"pathspec '{site_pathspec}'. Refusing to describe a commit whose "
                "contents cannot be accounted for."
            )
        relative.append(path[len(prefix):])
    return relative


def _attribute(udmi_root: str, site_model: str,
               changes: List[str]) -> Tuple[List[Dict[str, Any]], List[str]]:
    """Splits site-model-relative changed paths into device groups and site files.

    Device ids are read from the path and then cross-checked against the site
    model's own `devices/` directory. A path can name a device the model does
    not contain (a device deleted from the model after it was tested); it is
    still reported, flagged `known: False`, and no metadata is invented for it.
    """
    from workbench.server.discovery import list_device_ids

    # Only the id set matters here, and list_device_ids yields exactly the ids
    # list_devices would, without parsing every device's metadata.json.
    known = set(list_device_ids(udmi_root, site_model))

    grouped: Dict[str, List[str]] = {}
    site_changes: List[str] = []
    for path in changes:
        device_id = device_for_path(path)
        if device_id is None:
            site_changes.append(path)
        else:
            grouped.setdefault(device_id, []).append(path)

    devices = [
        {
            "device_id": device_id,
            "files": sorted(files),
            "file_count": len(files),
            "known": device_id in known,
        }
        for device_id, files in sorted(grouped.items())
    ]
    return devices, site_changes


def build_message(site_model: str, devices: List[Dict[str, Any]],
                  site_changes: List[str]) -> str:
    """Builds the commit message for a Workbench results commit.

    The message describes only what git reported: how many devices have changed
    files, which ones, and how many site-level files came with them. It does not
    characterise those files.

    An earlier version opened with "Sequencer results for N device(s)" and
    carried "Source: UDMI Workbench sequencer run". Neither is knowable here.
    `preview()` reports every pending change in the site model, whatever put it
    there, so the same wording sat above registrar key material, reflector
    certificates and discovered-device cloud metadata and described all of it as
    sequencer output. A commit message is the durable record of a change; it may
    not assert a provenance the code did not establish.

    The subject states the real device count even when the body truncates the
    name list, so the scale of a site-wide commit is never understated.
    """
    names = [device["device_id"] for device in devices]
    listed = names[:MESSAGE_DEVICE_LIMIT]
    remainder = len(names) - len(listed)
    lines = [
        f"Site model changes for {len(names)} device(s) in {_site_label(site_model)}",
        "",
        "Devices: " + (
            (", ".join(listed) + (f", and {remainder} more" if remainder else ""))
            if listed
            else "(none - no device files changed)"
        ),
        f"Site model: {site_model}",
        "Committed by: UDMI Workbench",
        f"Recorded: {datetime.now(timezone.utc).isoformat(timespec='seconds')}",
    ]
    if site_changes:
        lines.append(f"Site-level files: {len(site_changes)}")
    return "\n".join(lines) + "\n"


def udmi_repo_reason(repo: str) -> str:
    """The single copy of the hard block message for the UDMI checkout."""
    return (
        f"The site model is inside the UDMI checkout itself ({repo}). "
        "The Workbench never commits to, switches branches in, or pushes the UDMI "
        "repository. Committing sequencer results is only supported for site models "
        "kept in their own repository outside the UDMI checkout."
    )


def preview(udmi_root: str, site_model: str) -> Dict[str, Any]:
    """Reports exactly what a site-level commit would do, and any blocking reason.

    Every key is always present so the UI can render a fully explained disabled
    dialog; values are populated as far as they can be determined even when the
    commit is blocked.

    Two path conventions meet here and are deliberately kept apart:
      * `path` is the pathspec handed to git, so it is relative to the
        repository root (`udmi` for the staging lab).
      * `changes`, `site_changes` and `devices[].files` are relative to the
        site model directory, because that is what the operator selected.
    """
    from workbench.server.discovery import resolve_site_model

    site_dir = os.path.realpath(resolve_site_model(udmi_root, site_model))

    state: Dict[str, Any] = {
        "committable": False,
        "reason": None,
        "repo": None,
        "path": None,
        "changes": [],
        "change_count": 0,
        "repo_is_udmi": False,
        "current_branch": None,
        "branches": [],
        "remotes": [],
        "default_message": build_message(site_model, [], []),
        "devices": [],
        "site_changes": [],
        "device_count": 0,
    }

    repo = _repo_root(site_dir)
    if repo:
        state["repo"] = repo
        state["path"] = os.path.relpath(site_dir, repo)
        state["repo_is_udmi"] = repo == os.path.realpath(udmi_root)
        state["current_branch"] = _current_branch(repo)
        state["branches"] = _branches(repo)
        state["remotes"] = _remotes(repo)

    if not repo:
        state["reason"] = (
            f"'{site_dir}' is not inside a git repository, so results cannot be "
            "committed. Site models tracked for historical results must be version "
            "controlled."
        )
        return state

    if state["repo_is_udmi"]:
        state["reason"] = udmi_repo_reason(repo)
        return state

    relative = state["path"]
    if _git(repo, "check-ignore", "-q", relative).returncode == 0:
        state["reason"] = (
            f"'{relative}' is excluded by .gitignore in {repo}, so results cannot be "
            "committed. Un-ignore the site model path in that repository first."
        )
        return state

    # --untracked-files=all is load-bearing, not a tuning knob. Git's default
    # collapses an untracked tree to its topmost directory, so a site model
    # whose results have never been committed reports the single entry `out/`.
    # That entry names no device, which would put an entire run's results in
    # the site bucket and tell the operator nothing about what is changing.
    status = _git(repo, "status", "--porcelain", "--untracked-files=all", "--", relative)
    if status.returncode != 0:
        raise CommitError(f"git status failed: {status.stderr.strip()}")
    state["changes"] = _site_relative(
        relative, [line[3:] for line in status.stdout.splitlines() if line.strip()]
    )
    state["change_count"] = len(state["changes"])

    if not state["changes"]:
        state["reason"] = f"No uncommitted changes under '{relative}'. Nothing to commit."
        return state

    devices, site_changes = _attribute(udmi_root, site_model, state["changes"])
    state["devices"] = devices
    state["site_changes"] = site_changes
    state["device_count"] = len(devices)
    state["default_message"] = build_message(site_model, devices, site_changes)

    state["committable"] = True
    return state


def _validate_new_branch(repo: str, branch: str, existing: List[str]) -> None:
    """Fails unless `branch` is a valid, unused local branch name."""
    if branch.startswith("-"):
        raise CommitError(f"Invalid branch name '{branch}': names may not start with '-'.")
    if _git(repo, "check-ref-format", "--branch", branch).returncode != 0:
        raise CommitError(
            f"Invalid branch name '{branch}': rejected by git check-ref-format."
        )
    if branch in existing:
        raise CommitError(
            f"Branch '{branch}' already exists in {repo}. Choose a different name, or "
            "commit onto the existing branch without requesting branch creation."
        )


def _select_branch(repo: str, plan: Dict[str, Any], branch: Optional[str],
                   create_branch: bool) -> Dict[str, Any]:
    """Puts the repository on the requested branch, or fails explicitly."""
    current = plan["current_branch"]
    if create_branch:
        if not branch:
            raise CommitError("Branch creation requested but no 'branch' name was given.")
        _validate_new_branch(repo, branch, plan["branches"])
        result = _git(repo, "switch", "-c", branch)
        if result.returncode != 0:
            raise CommitError(
                f"git switch -c {branch} failed: {(result.stderr or result.stdout).strip()}"
            )
        return {"branch": branch, "branch_created": True}

    if branch and branch != current:
        if branch not in plan["branches"]:
            raise CommitError(
                f"Branch '{branch}' does not exist in {repo}. Existing branches: "
                f"{', '.join(plan['branches']) or '(none)'}. Request branch creation to "
                "make a new one."
            )
        result = _git(repo, "switch", branch)
        if result.returncode != 0:
            raise CommitError(
                f"git switch {branch} failed: {(result.stderr or result.stdout).strip()}"
            )
        return {"branch": branch, "branch_created": False}

    if not current:
        raise CommitError(
            f"HEAD is detached in {repo}. Specify a branch to commit the results onto."
        )
    return {"branch": current, "branch_created": False}


def _push(repo: str, branch: str, remote: str, remotes: List[str]) -> str:
    """Pushes `branch` to `remote`, setting upstream on first push."""
    if remote not in remotes:
        raise CommitError(
            f"Remote '{remote}' is not configured in {repo}. Available remotes: "
            f"{', '.join(remotes) or '(none)'}."
        )
    upstream = _git(repo, "rev-parse", "--abbrev-ref", "--symbolic-full-name",
                    f"{branch}@{{upstream}}")
    args = ["push"] + (["--set-upstream"] if upstream.returncode != 0 else []) + [remote, branch]
    result = _git(repo, *args)
    if result.returncode != 0:
        raise CommitError(
            f"git {' '.join(args)} failed: {(result.stderr or result.stdout).strip()}"
        )
    return (result.stderr or result.stdout).strip()


def commit(
    udmi_root: str,
    site_model: str,
    *,
    message: Optional[str] = None,
    branch: Optional[str] = None,
    create_branch: bool = False,
    push: bool = False,
    remote: Optional[str] = None,
    correlation_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Stages and commits a site model's sequencer results. User-initiated only."""
    if not site_model:
        raise CommitError("Missing required field: 'site_model'")
    if push and not remote:
        raise CommitError("Push requested but no 'remote' was given.")

    # Single enforcement point: nothing below runs for a blocked repository,
    # including the UDMI checkout itself.
    plan = preview(udmi_root, site_model)
    if not plan["committable"]:
        raise CommitError(plan["reason"])

    repo = plan["repo"]
    relative = plan["path"]
    final_message = (
        message
        if message and message.strip()
        else plan["default_message"]
    )

    selection = _select_branch(repo, plan, branch, create_branch)

    staged = _git(repo, "add", "--", relative)
    if staged.returncode != 0:
        raise CommitError(f"git add failed: {staged.stderr.strip()}")

    # Restricting the commit to this pathspec keeps work elsewhere in the
    # repository - including other site models - out of the commit.
    committed = _git(repo, "commit", "-m", final_message, "--", relative)
    if committed.returncode != 0:
        detail = (committed.stderr or committed.stdout).strip()
        if "user.email" in detail or "user.name" in detail:
            raise CommitError(
                "git identity is not configured in this repository. Set user.name and "
                f"user.email before committing results. Git reported: {detail}"
            )
        raise CommitError(f"git commit failed: {detail}")

    revision = _git(repo, "rev-parse", "HEAD").stdout.strip()
    push_output = _push(repo, selection["branch"], remote, plan["remotes"]) if push else None
    device_ids = [device["device_id"] for device in plan["devices"]]

    SERVER_LOGGER.info(
        "ResultsCommit",
        "results.committed",
        correlation_id=correlation_id,
        context={"siteModel": site_model, "deviceCount": plan["device_count"]},
        details={"repo": repo, "path": relative, "revision": revision,
                 "fileCount": plan["change_count"], "devices": device_ids,
                 "siteFileCount": len(plan["site_changes"]),
                 "branch": selection["branch"],
                 "branchCreated": selection["branch_created"], "pushed": bool(push),
                 "remote": remote if push else None},
    )

    return {
        "status": "COMMITTED",
        "repo": repo,
        "path": relative,
        "revision": revision,
        "short_revision": revision[:12],
        "message": final_message,
        "files": plan["changes"],
        "file_count": plan["change_count"],
        "devices": device_ids,
        "device_count": plan["device_count"],
        "site_file_count": len(plan["site_changes"]),
        "branch": selection["branch"],
        "branch_created": selection["branch_created"],
        "pushed": bool(push),
        "remote": remote if push else None,
        "push_output": push_output,
    }
