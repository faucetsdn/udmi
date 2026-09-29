"""Tests for results commit (Layer 4), exercised against real git repositories.

Every repository in this module is a genuine `git init` under `tmp_path`,
including a real bare repository used as a push remote, so branch selection,
branch creation, pathspec restriction, and pushing are verified end to end
rather than mocked. Nothing here touches the real UDMI checkout.

The scope under test is the SITE MODEL, not one device. Two layouts are
covered, because both exist in the field: the site model at the repository
root, and the site model in a subdirectory (`<repo>/udmi`, which is how the
staging lab is laid out and where an assumption that repo == site model would
silently commit the wrong pathspec).
"""

import json
import os
import subprocess

import pytest

from workbench.server.results_commit import (
    CommitError,
    build_message,
    commit,
    device_for_path,
    preview,
)

DEVICE_ID = "AHU-1"
OTHER_DEVICE_ID = "CGW-2"
TEST_NAME = "system_mode"


def _run(cwd, *args):
    result = subprocess.run(["git", "-C", str(cwd), *args],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, f"git {' '.join(args)} failed: {result.stderr}"
    return result.stdout.strip()


def _run_bare(git_dir, *args):
    """Inspects a bare repository; `--git-dir` avoids safe.bareRepository rules."""
    result = subprocess.run(["git", f"--git-dir={git_dir}", *args],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, f"git {' '.join(args)} failed: {result.stderr}"
    return result.stdout.strip()


def _write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)


def _make_site_model(site_dir, device_ids=(DEVICE_ID,)):
    """Creates the minimum layout that discovery.resolve_site_model accepts."""
    _write(os.path.join(site_dir, "cloud_iot_config.json"),
           json.dumps({"site_name": "TEST", "registry_id": "ZZ-TRI-FECTA"}))
    for device_id in device_ids:
        _write(os.path.join(site_dir, "devices", device_id, "metadata.json"),
               json.dumps({"version": "1.5.0"}))


def _write_results(site_dir, device_id=DEVICE_ID, content="RESULT pass system_mode\n"):
    """Writes every artefact one sequencer run leaves behind for a device.

    A run does not only write `out/devices/<id>/tests`: it also writes the
    machine-readable record at `out/sequencer_<id>.json` and rewrites the
    device's own `devices/<id>/out` files. All three are written here because
    all three must be attributed to the device and committed together.
    """
    _write(os.path.join(site_dir, "out", "devices", device_id, "tests", TEST_NAME,
                        "RESULT.log"), content)
    _write(os.path.join(site_dir, "out", "devices", device_id, "results.md"),
           f"# {device_id}\n")
    _write(os.path.join(site_dir, "out", f"sequencer_{device_id}.json"),
           json.dumps({"device_id": device_id}))
    _write(os.path.join(site_dir, "devices", device_id, "out", "generated_config.json"),
           json.dumps({"system": {}}))
    _write(os.path.join(site_dir, "devices", device_id, "out", "metadata_norm.json"),
           json.dumps({"version": "1.5.0"}))
    _write(os.path.join(site_dir, "devices", device_id, "out", "errors.map"), "")


def _write_site_artifact(site_dir):
    """A registrar output that belongs to the site, not to any device."""
    _write(os.path.join(site_dir, "out", "registration_summary.csv"), "device,status\n")


def _init_repo(path):
    """Initialises a real git repository with a local commit identity."""
    os.makedirs(path, exist_ok=True)
    subprocess.run(["git", "init", "-b", "main", str(path)],
                   capture_output=True, text=True, check=True, timeout=30)
    _run(path, "config", "user.name", "Workbench Test")
    _run(path, "config", "user.email", "workbench@example.com")
    return str(path)


@pytest.fixture
def site_repo(tmp_path):
    """A real git repo whose root IS the site model, with a committed baseline."""
    repo = tmp_path / "lab-site"
    _init_repo(repo)
    _make_site_model(str(repo))
    _run(repo, "add", "-A")
    _run(repo, "commit", "-m", "baseline site model")
    _write_results(str(repo))
    return str(repo)


@pytest.fixture
def nested_repo(tmp_path):
    """The staging lab's layout: the site model is `<repo>/udmi`, not the repo.

    The repository also carries a file outside the site model, which is what
    makes this fixture worth having: it proves the commit pathspec is the site
    model rather than the repository.
    """
    repo = tmp_path / "ZZ-TEST-SITE"
    _init_repo(repo)
    site = repo / "udmi"
    _make_site_model(str(site), device_ids=(DEVICE_ID, OTHER_DEVICE_ID))
    _write(os.path.join(str(repo), "README.md"), "lab repository\n")
    _run(repo, "add", "-A")
    _run(repo, "commit", "-m", "baseline site model")
    return str(repo), str(site)


@pytest.fixture
def udmi_root(tmp_path):
    """A directory standing in for the UDMI checkout, used as the path base."""
    root = tmp_path / "udmi-root"
    root.mkdir()
    return str(root)


# ------------------------------------------------------------ attribution ---
@pytest.mark.parametrize("path,expected", [
    ("out/devices/AHU-1/tests/system_mode/RESULT.log", "AHU-1"),
    ("out/devices/AHU-1/results.md", "AHU-1"),
    ("out/devices/AHU-1/RESULT.log", "AHU-1"),
    ("out/sequencer_AHU-1.json", "AHU-1"),
    ("devices/AHU-1/out/generated_config.json", "AHU-1"),
    ("devices/AHU-1/out/metadata_norm.json", "AHU-1"),
    ("devices/AHU-1/out/errors.map", "AHU-1"),
    ("devices/AHU-1/metadata.json", "AHU-1"),
    ("out/registration_summary.csv", None),
    ("out/registration_summary.json", None),
    ("cloud_iot_config.json", None),
    ("site_defaults.json", None),
    ("out/sequencer.json", None),
])
def test_device_for_path(path, expected):
    assert device_for_path(path) == expected


def test_preview_groups_changes_by_device_and_site(site_repo, udmi_root):
    _write_results(site_repo, OTHER_DEVICE_ID)
    _write_site_artifact(site_repo)

    plan = preview(udmi_root, site_repo)

    assert plan["committable"] is True
    assert plan["device_count"] == 2
    assert [device["device_id"] for device in plan["devices"]] == [DEVICE_ID, OTHER_DEVICE_ID]

    # Every artefact of a run lands on its device, including the two the old
    # per-device results-directory scope left uncommitted.
    first = plan["devices"][0]
    assert first["file_count"] == 6
    assert f"out/sequencer_{DEVICE_ID}.json" in first["files"]
    assert f"devices/{DEVICE_ID}/out/generated_config.json" in first["files"]
    assert f"devices/{DEVICE_ID}/out/errors.map" in first["files"]

    assert plan["site_changes"] == ["out/registration_summary.csv"]
    assert plan["change_count"] == 13


def test_preview_flags_a_device_the_site_model_does_not_contain(site_repo, udmi_root):
    """Results can outlive the `devices/` entry that produced them.

    Only the `out/` artefacts are written here: creating `devices/RETIRED-9`
    would put the device back in the model and defeat the cross-check.
    """
    _write(os.path.join(site_repo, "out", "devices", "RETIRED-9", "results.md"), "# gone\n")
    _write(os.path.join(site_repo, "out", "sequencer_RETIRED-9.json"), "{}")

    plan = preview(udmi_root, site_repo)
    by_id = {device["device_id"]: device for device in plan["devices"]}

    assert by_id[DEVICE_ID]["known"] is True
    assert by_id["RETIRED-9"]["known"] is False
    assert by_id["RETIRED-9"]["file_count"] == 2


def test_preview_reports_paths_relative_to_a_nested_site_model(nested_repo, udmi_root):
    """`path` is repo-relative for git; every file list is site-relative."""
    repo, site = nested_repo
    _write_results(site)
    _write_site_artifact(site)

    plan = preview(udmi_root, site)

    assert plan["repo"] == os.path.realpath(repo)
    assert plan["path"] == "udmi"
    assert plan["committable"] is True
    assert all(not path.startswith("udmi/") for path in plan["changes"])
    assert f"out/sequencer_{DEVICE_ID}.json" in plan["changes"]
    assert plan["devices"][0]["device_id"] == DEVICE_ID
    assert plan["site_changes"] == ["out/registration_summary.csv"]


def test_preview_accepts_the_repository_root_of_a_nested_site_model(nested_repo, udmi_root):
    """resolve_site_model descends into `udmi/`, and the pathspec follows it."""
    repo, _site = nested_repo

    plan = preview(udmi_root, repo)

    assert plan["path"] == "udmi"
    assert plan["repo"] == os.path.realpath(repo)


def test_preview_counts_every_untracked_file_not_the_collapsed_directory(tmp_path, udmi_root):
    """A never-committed `out/` must not be reported as one nameless change."""
    repo = tmp_path / "fresh-site"
    _init_repo(repo)
    _make_site_model(str(repo))
    _run(repo, "add", "cloud_iot_config.json")
    _run(repo, "commit", "-m", "config only")
    _write_results(str(repo))

    plan = preview(udmi_root, str(repo))

    assert "out/" not in plan["changes"]
    assert plan["device_count"] == 1
    assert plan["devices"][0]["file_count"] == 7  # six run artefacts plus metadata.json


# ------------------------------------------------------------- blocking ----
def test_preview_non_repo_is_blocked(tmp_path, udmi_root):
    site = tmp_path / "plain-site"
    _make_site_model(str(site))
    _write_results(str(site))

    plan = preview(udmi_root, str(site))

    assert plan["committable"] is False
    assert "not inside a git repository" in plan["reason"]
    assert plan["repo"] is None
    assert plan["path"] is None
    assert plan["repo_is_udmi"] is False
    assert plan["branches"] == []
    assert plan["remotes"] == []
    assert plan["devices"] == []
    assert plan["site_changes"] == []
    assert plan["device_count"] == 0
    assert "Committed by: UDMI Workbench" in plan["default_message"]


def test_preview_blocks_udmi_repo(tmp_path):
    """A site model living inside the UDMI checkout itself is hard-blocked."""
    udmi = tmp_path / "udmi"
    _init_repo(udmi)
    site = udmi / "sites" / "udmi_site_model"
    _make_site_model(str(site))
    _write_results(str(site))

    plan = preview(str(udmi), "sites/udmi_site_model")

    assert plan["repo_is_udmi"] is True
    assert plan["committable"] is False
    assert "UDMI checkout" in plan["reason"]
    # The block stands on the repository identity alone. The old wording also
    # claimed udmi_site_model was "a fixture of the UDMI repo", which is false:
    # it is a separate upstream repository (faucetsdn/udmi_site_model) that the
    # UDMI checkout gitignores.
    assert "fixture" not in plan["reason"]
    assert plan["repo"] == os.path.realpath(str(udmi))
    assert plan["path"] == os.path.join("sites", "udmi_site_model")
    assert plan["current_branch"] == "main"


def test_commit_against_udmi_repo_makes_no_git_write(tmp_path):
    udmi = tmp_path / "udmi"
    _init_repo(udmi)
    site = udmi / "sites" / "udmi_site_model"
    _make_site_model(str(site))
    _run(udmi, "add", "-A")
    _run(udmi, "commit", "-m", "udmi baseline")
    _write_results(str(site))

    before_head = _run(udmi, "rev-parse", "HEAD")
    before_branch = _run(udmi, "rev-parse", "--abbrev-ref", "HEAD")
    before_status = _run(udmi, "status", "--porcelain")

    with pytest.raises(CommitError) as excinfo:
        commit(str(udmi), "sites/udmi_site_model",
               branch="results", create_branch=True, push=True, remote="origin")

    assert "UDMI checkout" in str(excinfo.value)
    assert _run(udmi, "rev-parse", "HEAD") == before_head
    assert _run(udmi, "rev-parse", "--abbrev-ref", "HEAD") == before_branch
    assert _run(udmi, "status", "--porcelain") == before_status
    assert _run(udmi, "for-each-ref", "--format=%(refname:short)", "refs/heads") == "main"


def test_preview_blocks_when_nothing_changed(site_repo, udmi_root):
    commit(udmi_root, site_repo)

    plan = preview(udmi_root, site_repo)

    assert plan["committable"] is False
    assert "Nothing to commit" in plan["reason"]
    assert plan["change_count"] == 0
    assert plan["devices"] == []


def test_preview_blocks_an_ignored_site_model(tmp_path, udmi_root):
    """A site model the repository excludes cannot record anything.

    This is the situation `sites/udmi_site_model` is actually in: its own
    .gitignore excludes `out/` and `devices/*/out/`, so results written there
    can never be committed. The block is reported as exclusion, which is the
    true reason, and is independent of the separate UDMI-checkout block.

    The site model is deliberately left untracked: git only applies exclude
    rules to paths that are not already in the index, so an ignored-but-tracked
    directory is not excluded at all and must not be reported as such.
    """
    repo = tmp_path / "ignored-lab"
    _init_repo(repo)
    site = repo / "udmi"
    _make_site_model(str(site))
    _write_results(str(site))
    _write(os.path.join(str(repo), ".gitignore"), "udmi/\n")
    _run(repo, "add", ".gitignore")
    _run(repo, "commit", "-m", "exclude the site model")

    plan = preview(udmi_root, str(site))

    assert plan["committable"] is False
    assert ".gitignore" in plan["reason"]
    assert "udmi" in plan["reason"]
    assert plan["devices"] == []


# ---------------------------------------------------------------- state ----
def test_preview_reports_branches_remotes_and_current(site_repo, udmi_root, tmp_path):
    _run(site_repo, "branch", "archive")
    remote_path = tmp_path / "remote.git"
    subprocess.run(["git", "init", "--bare", str(remote_path)],
                   capture_output=True, text=True, check=True, timeout=30)
    _run(site_repo, "remote", "add", "origin", str(remote_path))
    _run(site_repo, "remote", "add", "backup", str(remote_path))

    plan = preview(udmi_root, site_repo)

    assert plan["committable"] is True
    assert plan["reason"] is None
    assert plan["current_branch"] == "main"
    assert plan["branches"] == ["archive", "main"]
    assert plan["remotes"] == ["backup", "origin"]
    assert plan["change_count"] == len(plan["changes"]) == 6
    assert plan["repo_is_udmi"] is False


# --------------------------------------------------------------- commit ----
def test_commit_uses_custom_message_verbatim(site_repo, udmi_root):
    result = commit(udmi_root, site_repo, message="Lab run 42 results")

    assert result["status"] == "COMMITTED"
    assert result["message"] == "Lab run 42 results"
    assert _run(site_repo, "log", "-1", "--pretty=%B").strip() == "Lab run 42 results"
    assert result["branch"] == "main"
    assert result["branch_created"] is False
    assert result["pushed"] is False
    assert result["remote"] is None
    assert result["push_output"] is None
    assert result["revision"].startswith(result["short_revision"])
    assert result["devices"] == [DEVICE_ID]
    assert result["device_count"] == 1


def test_commit_without_message_uses_generated_default(site_repo, udmi_root):
    _write_results(site_repo, OTHER_DEVICE_ID)
    _write_site_artifact(site_repo)

    result = commit(udmi_root, site_repo)

    body = _run(site_repo, "log", "-1", "--pretty=%B")
    assert result["message"].startswith("Site model changes for 2 device(s) in lab-site")
    assert f"Devices: {DEVICE_ID}, {OTHER_DEVICE_ID}" in body
    assert f"Site model: {site_repo}" in body
    assert "Committed by: UDMI Workbench" in body
    assert "Site-level files: 1" in body


def test_commit_records_every_artifact_of_a_run(site_repo, udmi_root):
    """The per-device scope committed only `out/devices/<id>/tests`."""
    commit(udmi_root, site_repo)

    tracked = _run(site_repo, "ls-tree", "-r", "--name-only", "HEAD").splitlines()

    assert f"out/devices/{DEVICE_ID}/tests/{TEST_NAME}/RESULT.log" in tracked
    assert f"out/sequencer_{DEVICE_ID}.json" in tracked
    assert f"devices/{DEVICE_ID}/out/generated_config.json" in tracked
    assert f"devices/{DEVICE_ID}/out/metadata_norm.json" in tracked
    assert f"devices/{DEVICE_ID}/out/errors.map" in tracked
    assert _run(site_repo, "status", "--porcelain") == ""


def test_commit_only_stages_the_site_model_pathspec(nested_repo, udmi_root):
    """A lab repository can hold more than the site model; it stays untouched."""
    repo, site = nested_repo
    _write_results(site)
    _write(os.path.join(repo, "README.md"), "edited outside the site model\n")

    commit(udmi_root, site)

    dirty = _run(repo, "status", "--porcelain")
    assert "README.md" in dirty
    assert "udmi/out" not in dirty
    tracked = _run(repo, "ls-tree", "-r", "--name-only", "HEAD").splitlines()
    assert f"udmi/out/sequencer_{DEVICE_ID}.json" in tracked


def test_commit_creates_and_switches_branch(site_repo, udmi_root):
    result = commit(udmi_root, site_repo, branch="results/run-1", create_branch=True)

    assert result["branch"] == "results/run-1"
    assert result["branch_created"] is True
    assert _run(site_repo, "rev-parse", "--abbrev-ref", "HEAD") == "results/run-1"
    assert _run(site_repo, "rev-parse", "results/run-1") == result["revision"]


def test_create_branch_on_existing_name_fails(site_repo, udmi_root):
    _run(site_repo, "branch", "existing")
    head = _run(site_repo, "rev-parse", "HEAD")

    with pytest.raises(CommitError, match="already exists"):
        commit(udmi_root, site_repo, branch="existing", create_branch=True)

    assert _run(site_repo, "rev-parse", "HEAD") == head
    assert _run(site_repo, "rev-parse", "--abbrev-ref", "HEAD") == "main"


def test_create_branch_with_invalid_ref_name_fails(site_repo, udmi_root):
    with pytest.raises(CommitError) as excinfo:
        commit(udmi_root, site_repo, branch="bad name~1", create_branch=True)

    assert "bad name~1" in str(excinfo.value)
    assert _run(site_repo, "rev-parse", "--abbrev-ref", "HEAD") == "main"


def test_switch_to_missing_branch_fails(site_repo, udmi_root):
    with pytest.raises(CommitError) as excinfo:
        commit(udmi_root, site_repo, branch="nope")

    assert "does not exist" in str(excinfo.value)
    assert "main" in str(excinfo.value)
    assert _run(site_repo, "rev-parse", "--abbrev-ref", "HEAD") == "main"


def test_switch_to_existing_branch(site_repo, udmi_root):
    _run(site_repo, "branch", "archive")

    result = commit(udmi_root, site_repo, branch="archive")

    assert result["branch"] == "archive"
    assert result["branch_created"] is False
    assert _run(site_repo, "rev-parse", "--abbrev-ref", "HEAD") == "archive"


def test_commit_requires_a_site_model(udmi_root):
    with pytest.raises(CommitError, match="site_model"):
        commit(udmi_root, "")


# ----------------------------------------------------------------- push ----
def test_push_to_real_bare_remote(site_repo, udmi_root, tmp_path):
    remote_path = tmp_path / "origin.git"
    subprocess.run(["git", "init", "--bare", str(remote_path)],
                   capture_output=True, text=True, check=True, timeout=30)
    _run(site_repo, "remote", "add", "origin", str(remote_path))

    result = commit(udmi_root, site_repo, branch="results/push",
                    create_branch=True, push=True, remote="origin")

    assert result["pushed"] is True
    assert result["remote"] == "origin"
    assert result["push_output"]
    assert _run_bare(remote_path, "rev-parse", "results/push") == result["revision"]
    assert TEST_NAME in _run_bare(remote_path, "ls-tree", "-r", "--name-only",
                                  "results/push")


def test_push_to_unconfigured_remote_fails(site_repo, udmi_root, tmp_path):
    remote_path = tmp_path / "origin.git"
    subprocess.run(["git", "init", "--bare", str(remote_path)],
                   capture_output=True, text=True, check=True, timeout=30)
    _run(site_repo, "remote", "add", "origin", str(remote_path))

    with pytest.raises(CommitError) as excinfo:
        commit(udmi_root, site_repo, push=True, remote="upstream")

    message = str(excinfo.value)
    assert "upstream" in message
    assert "origin" in message
    # The commit itself succeeded before the push was rejected; nothing reached
    # the remote.
    assert _run_bare(remote_path, "for-each-ref", "refs/heads") == ""


# -------------------------------------------------------------- message ----
def _devices(*names):
    return [{"device_id": name, "files": [], "file_count": 0, "known": True}
            for name in names]


def test_build_message_states_devices_and_site():
    message = build_message("sites/lab", _devices("AHU-1", "CGW-2"), [])

    assert message.startswith("Site model changes for 2 device(s) in lab")
    assert "Devices: AHU-1, CGW-2" in message
    assert "Site model: sites/lab" in message
    assert "Committed by: UDMI Workbench" in message
    assert "Site-level files:" not in message
    assert message.endswith("\n")


def test_build_message_never_claims_the_changes_are_sequencer_results():
    """The message reports what changed, not what produced it.

    `preview()` reports every pending change in the site model. In a model that
    gitignores `out/` the only changed files are things like registrar key
    material and reflector certificates, and a message reading "Sequencer
    results for 2 device(s) ... Source: UDMI Workbench sequencer run" over that
    list is simply false. Provenance is not established anywhere in this module,
    so it must not appear in the commit record.
    """
    message = build_message(
        "sites/lab",
        _devices("AHU-1", "GAT-123"),
        ["reflector/ca.crt", "extras/ACME-2301/cloud_model.json"],
    )

    lowered = message.lower()
    assert "sequencer" not in lowered
    assert "results" not in lowered
    assert "Site model changes for 2 device(s) in lab" in message
    assert "Site-level files: 2" in message


def test_build_message_names_a_nested_site_model_after_its_repository():
    """`<repo>/udmi` must not be reported as a site called "udmi"."""
    message = build_message("/labs/ZZ-TEST-SITE/udmi", _devices("AHU-1"), [])

    assert "in ZZ-TEST-SITE" in message


def test_build_message_truncates_the_device_list_but_not_the_count():
    names = [f"DEV-{index:02d}" for index in range(20)]

    message = build_message("sites/lab", _devices(*names), ["out/registration_summary.csv"])

    assert "Site model changes for 20 device(s)" in message
    assert "DEV-11" in message
    assert "DEV-12" not in message
    assert "and 8 more" in message
    assert "Site-level files: 1" in message


def test_build_message_is_explicit_when_no_device_files_changed():
    message = build_message("sites/lab", [], ["cloud_iot_config.json"])

    assert "Site model changes for 0 device(s)" in message
    assert "Devices: (none - no device files changed)" in message
