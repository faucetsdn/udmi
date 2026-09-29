"""Tests for the Workbench support bundle export (in-process tarfile builder)."""

import getpass
import json
import os
import subprocess
import tarfile

import pytest

from workbench.server import support_bundle
from workbench.server.support_bundle import SupportBundleBusyError, SupportBundleError

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

PRIVATE_FILES = (
    "reflector/rsa_private.pem",
    "reflector/rsa_private.pkcs8",
    "reflector/server.key",
    "devices/AHU-1/rsa_private.pem",
    "devices/AHU-1/rsa_private.pkcs8",
    "devices/GAT-123/ec_private.pem",
    "devices/GAT-123/ec_private.pkcs8",
)
PUBLIC_FILES = (
    "cloud_iot_config.json",
    "reflector/ca.crt",
    "reflector/rsa_private.crt",
    "devices/AHU-1/rsa_public.pem",
    "devices/AHU-1/rsa_private.crt",
    "devices/AHU-1/metadata.json",
    "devices/GAT-123/ec_public.pem",
)


def _write(path, content="x\n"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)


def _make_site(site_dir):
    for rel in PRIVATE_FILES + PUBLIC_FILES:
        _write(os.path.join(site_dir, rel), "{}\n" if rel.endswith(".json") else "key\n")
    _write(os.path.join(site_dir, ".git", "config"), "[core]\n")
    _write(os.path.join(site_dir, ".gitignore"), "out/\n")
    return os.path.realpath(site_dir)


def _make_root(tmp_path):
    root = tmp_path / "udmi"
    _write(str(root / "out" / "devices" / "AHU-1" / "tests" / "RESULT.log"), "pass\n")
    _write(str(root / "out" / "sequencer_config.json"), json.dumps({"from": "out"}))
    return str(root)


def _make_cache(tmp_path, names=("sequencer_config.json", "pubber_config.json")):
    cache = tmp_path / "cache"
    cache.mkdir()
    for name in names:
        _write(str(cache / name), json.dumps({"from": "cache", "name": name}))
    return str(cache)


def _members(path):
    with tarfile.open(path, "r:gz") as archive:
        return [m.name.rstrip("/") for m in archive.getmembers()]


@pytest.fixture
def env(tmp_path):
    udmi_root = _make_root(tmp_path)
    site = _make_site(str(tmp_path / "sites" / "staging" / "ZZ-LAB" / "udmi"))
    cache = _make_cache(tmp_path)
    return {"root": udmi_root, "site": site, "cache": cache,
            "roots": [str(tmp_path / "sites")]}


def _export(env, **kwargs):
    return support_bundle.create_bundle(
        env["root"], env["site"], site_roots=env["roots"],
        cached_config_dir=env["cache"], **kwargs,
    )


# ------------------------------------------------------------ happy path ---
def test_bundle_contains_site_out_and_cached_configs_without_private_keys(env):
    result = _export(env)

    archive = support_bundle.bundle_path(env["root"], result["bundle_id"])
    members = _members(archive)
    prefix = "site_model/ZZ-LAB"  # canonical name: parent of the nested udmi/ dir
    for rel in PUBLIC_FILES:
        assert f"{prefix}/{rel}" in members, rel
    for rel in PRIVATE_FILES:
        assert f"{prefix}/{rel}" not in members, rel
    assert not [m for m in members if support_bundle.PRIVATE_KEY_PATTERN.search(m)]
    assert result["excluded_secret_count"] == len(PRIVATE_FILES)
    assert "out/devices/AHU-1/tests/RESULT.log" in members
    assert "out/pubber_config.json" in members
    assert result["cached_configs"] == ["sequencer_config.json", "pubber_config.json"]
    assert {m.split("/")[0] for m in members} == {"out", "site_model"}
    assert result["member_count"] == len(members)
    assert result["filename"] == os.path.basename(archive)


def test_cached_config_supersedes_out_file_of_same_name(env):
    result = _export(env)
    archive = support_bundle.bundle_path(env["root"], result["bundle_id"])
    with tarfile.open(archive, "r:gz") as tar:
        names = tar.getnames()
        assert names.count("out/sequencer_config.json") == 1
        body = json.load(tar.extractfile("out/sequencer_config.json"))
    assert body["from"] == "cache"


def test_vcs_metadata_and_previous_exports_are_not_archived(env):
    first = _export(env)
    second = _export(env)
    members = _members(support_bundle.bundle_path(env["root"], second["bundle_id"]))
    assert not [m for m in members if "/.git" in m or m.endswith(".gitignore")]
    assert not [m for m in members if "workbench_exports" in m]
    assert first["bundle_id"] != second["bundle_id"]


def test_no_host_path_or_owner_leaks_into_archive(env):
    result = _export(env)
    archive = support_bundle.bundle_path(env["root"], result["bundle_id"])
    home = os.path.realpath(os.path.expanduser("~")).lstrip("/")
    user = getpass.getuser()
    with tarfile.open(archive, "r:gz") as tar:
        infos = tar.getmembers()
    assert not [i.name for i in infos if home in i.name or user in i.name]
    assert {(i.uid, i.gid, i.uname, i.gname) for i in infos} == {(0, 0, "", "")}


def test_export_writes_nothing_outside_its_own_directory(env, monkeypatch):
    monkeypatch.setenv("UDMI_REGISTRY_SUFFIX", "_must_not_relocate")
    before_out = sorted(os.listdir(os.path.join(env["root"], "out")))
    result = _export(env)
    after_out = sorted(os.listdir(os.path.join(env["root"], "out")))
    assert set(after_out) - set(before_out) == {"workbench_exports"}
    assert os.listdir(os.path.join(support_bundle.exports_root(env["root"]),
                                   result["bundle_id"])) == [result["filename"]]
    assert os.path.isdir(env["site"])


def test_symlink_is_archived_as_link_and_not_followed(env, tmp_path):
    outside = tmp_path / "outside"
    _write(str(outside / "secret_notes.txt"), "do not bundle\n")
    os.symlink(outside, os.path.join(env["site"], "linked"))
    members = _members(support_bundle.bundle_path(env["root"], _export(env)["bundle_id"]))
    assert "site_model/ZZ-LAB/linked" in members
    assert not [m for m in members if "secret_notes" in m]


# ------------------------------------------------------ negative controls ---
def test_key_filter_is_what_keeps_keys_out(env, monkeypatch):
    """Negative verification: disable the filter and keys must reach the archive,
    where the post-write check catches them and deletes the bundle."""
    monkeypatch.setattr(support_bundle, "PRIVATE_KEY_PATTERN",
                        support_bundle.re.compile(r"(?!x)x"))
    real_verify = support_bundle._verify
    seen = {}

    def spy(path, site_name):
        seen["members"] = _members(path)
        return real_verify(path, site_name)

    monkeypatch.setattr(support_bundle, "_verify", spy)
    _export(env)
    assert "site_model/ZZ-LAB/reflector/rsa_private.pkcs8" in seen["members"]


def test_private_key_in_finished_archive_fails_and_deletes_bundle(env, monkeypatch):
    real_add_tree = support_bundle._add_tree

    def leaky(archive, source, prefix, excluded, skip_dirs, skip_top_files):
        real_add_tree(archive, source, prefix, excluded, skip_dirs, skip_top_files)
        if prefix.startswith("site_model"):
            archive.add(os.path.join(source, "reflector", "rsa_private.pkcs8"),
                        arcname=f"{prefix}/reflector/rsa_private.pkcs8")

    monkeypatch.setattr(support_bundle, "_add_tree", leaky)
    with pytest.raises(SupportBundleError, match="Private key material"):
        _export(env)
    assert os.listdir(support_bundle.exports_root(env["root"])) == []


def test_unreadable_directory_fails_instead_of_being_skipped(env):
    locked = os.path.join(env["site"], "devices", "AHU-1")
    os.chmod(locked, 0)
    try:
        if os.access(locked, os.R_OK):
            pytest.skip("running as a user that ignores directory permissions")
        with pytest.raises(SupportBundleError, match="Cannot read"):
            _export(env)
    finally:
        os.chmod(locked, 0o755)
    assert os.listdir(support_bundle.exports_root(env["root"])) == []


# ------------------------------------------------------------ rejections ---
def test_running_sequencer_blocks_export(env):
    with pytest.raises(SupportBundleBusyError, match="still running"):
        _export(env, running_sessions=[{"session_id": "s1", "running": True}])


def test_site_outside_registered_roots_is_rejected(env, tmp_path):
    other = _make_site(str(tmp_path / "elsewhere" / "site"))
    with pytest.raises(SupportBundleError, match="outside the UDMI checkout"):
        support_bundle.create_bundle(env["root"], other, site_roots=env["roots"],
                                     cached_config_dir=env["cache"])


def test_missing_site_model_field_is_rejected(env):
    with pytest.raises(SupportBundleError, match="Missing required field"):
        support_bundle.create_bundle(env["root"], "", cached_config_dir=env["cache"])


def test_display_name_rejects_unsafe_segment(tmp_path):
    site = _make_site(str(tmp_path / "bad name" / "udmi"))
    with pytest.raises(SupportBundleError, match="single safe path segment"):
        support_bundle.site_display_name(str(tmp_path), site)


@pytest.mark.parametrize("bundle_id", ["", "../etc", "ABC", "0" * 31, "g" * 32])
def test_bundle_path_rejects_invalid_ids(tmp_path, bundle_id):
    with pytest.raises(SupportBundleError, match="Invalid support bundle id"):
        support_bundle.bundle_path(str(tmp_path), bundle_id)


def test_bundle_path_unknown_id_is_not_found(tmp_path):
    with pytest.raises(SupportBundleError, match="not found"):
        support_bundle.bundle_path(str(tmp_path), "0" * 32)


def test_partial_archive_is_never_served(env, monkeypatch):
    def boom(path, site_name):
        raise SupportBundleError("verification failed")

    monkeypatch.setattr(support_bundle, "_verify", boom)
    with pytest.raises(SupportBundleError):
        _export(env)
    assert os.listdir(support_bundle.exports_root(env["root"])) == []


# --------------------------------------------------------- bin/support ---
def test_bin_support_is_not_modified_or_invoked():
    """The Workbench export must leave the CLI script exactly as committed."""
    diff = subprocess.run(["git", "diff", "--quiet", "HEAD", "--", "bin/support"],
                          cwd=REPO_ROOT, check=False)
    assert diff.returncode == 0, "bin/support differs from HEAD"
    with open(support_bundle.__file__, encoding="utf-8") as fh:
        source = fh.read()
    assert "subprocess" not in source


def test_special_file_fails_explicitly(env):
    os.mkfifo(os.path.join(env["site"], "devices", "AHU-1", "pipe"))
    with pytest.raises(SupportBundleError, match="not a regular file"):
        _export(env)
    assert os.listdir(support_bundle.exports_root(env["root"])) == []
