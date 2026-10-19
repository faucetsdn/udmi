"""Support/evidence bundle export (Layer 4).

Lab operators hand a failing device's evidence to its manufacturer. This module
builds that bundle in-process with `tarfile`. It collects the same material as
the `bin/support` CLI script -- the site model, the UDMI `out/` directory and the
cached tool configs `/tmp/{validator,registrar,sequencer,pubber}_config.json`
(archived under `out/`) -- but does not run that script, because a server-side,
externally shared export needs guarantees the script does not give:

  * The bundle is the site model the user selected. `bin/support` infers the
    site model from whichever cached tool config is newest.
  * Private key material is never archived. Matching files are skipped while
    the archive is written, and the finished archive is listed again: any
    member matching a private-key pattern fails the export and the archive is
    deleted.
  * No host detail leaks. Members are named `out/...` and
    `site_model/<site name>/...` (never an absolute path), and owner
    uid/gid/user/group are cleared from every tar header.
  * Nothing outside the export's own directory is written. `bin/support`
    copies the cached configs into `out/`, and with `UDMI_REGISTRY_SUFFIX` set
    it `mv`s `out/` and the site model.
  * Every export gets its own directory `out/workbench_exports/<id>/`, which is
    itself never archived, so exports can run concurrently.
"""

import os
import re
import shutil
import stat
import tarfile
import time
import uuid
from typing import Any, Dict, Iterable, List, Optional

from workbench.server import discovery
from workbench.server.logger import SERVER_LOGGER
from workbench.server.paths import display, within_any

EXPORTS_RELATIVE_DIR = os.path.join("out", "workbench_exports")

# Tool configs that bin/validator, bin/registrar, bin/sequencer and bin/pubber
# cache; bundled for the same reason bin/support bundles them.
CACHED_CONFIG_DIR = "/tmp"
CACHED_CONFIG_NAMES = (
    "validator_config.json",
    "registrar_config.json",
    "sequencer_config.json",
    "pubber_config.json",
)

# The private key files bin/keygen writes (rsa_/ec_private.pem and .pkcs8) and
# the CA server key bin/setup_ca writes. `*_private.crt` / `*_private.csr` are
# certificates and requests (public).
PRIVATE_KEY_PATTERN = re.compile(r"(_private\.pem|_private\.pkcs8|\.key)$")

# Version-control metadata, as excluded by `tar --exclude-vcs`.
VCS_NAMES = frozenset({
    ".git", ".gitignore", ".gitattributes", ".gitmodules",
    ".hg", ".hgignore", ".hgtags",
    ".bzr", ".bzrignore", ".bzrtags",
    ".svn", "CVS", ".cvsignore", "RCS", "SCCS", "_darcs", ".arch-ids", "{arch}",
})

BUNDLE_ID_PATTERN = re.compile(r"^[0-9a-f]{32}$")
ARCHIVE_NAME_PATTERN = re.compile(r"^udmi-support_\d{8}-\d{6}\.tgz$")
PARTIAL_SUFFIX = ".partial"
# gzip's own default (TarFile defaults to 9, which is slower for no real gain).
GZIP_LEVEL = 6

OUT_MEMBER = "out"
SITE_MEMBER_PARENT = "site_model"
SITE_NAME_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")


class SupportBundleError(Exception):
    """Raised when a support bundle cannot be produced or served."""


class SupportBundleBusyError(SupportBundleError):
    """Raised when a sequencer run is still writing the files to be bundled."""


def site_display_name(udmi_root: str, site_abs: str) -> str:
    """The Workbench's canonical site name (parent folder for `<site>/udmi`).

    Reuses discovery.describe_site_model so bundles are named exactly as the
    site model is shown in the UI, and validates it as one safe path segment.
    """
    name = discovery.describe_site_model(site_abs, udmi_root)["name"]
    if not SITE_NAME_PATTERN.match(name) or name in (".", ".."):
        raise SupportBundleError(
            f"Site model name {name!r} is not a single safe path segment "
            "([A-Za-z0-9._-]); cannot name it inside the support archive."
        )
    return name


def site_member_path(site_name: str) -> str:
    """Archive member for the site model."""
    return f"{SITE_MEMBER_PARENT}/{site_name}"


def exports_root(udmi_root: str) -> str:
    return os.path.join(udmi_root, EXPORTS_RELATIVE_DIR)


def _resolve_selected_site(udmi_root: str, site_model: str, site_roots: Iterable[str]) -> str:
    if not site_model:
        raise SupportBundleError("Missing required field: 'site_model'")
    site_abs = discovery.resolve_site_model(udmi_root, site_model)
    if not within_any(site_abs, [udmi_root, *site_roots]):
        raise SupportBundleError(
            f"Site model {site_abs} is outside the UDMI checkout and every registered "
            "site root. Register its containing directory under Site Roots first."
        )
    return os.path.realpath(site_abs)


def create_bundle(
    udmi_root: str,
    site_model: str,
    site_roots: Iterable[str] = (),
    running_sessions: Iterable[Dict[str, Any]] = (),
    correlation_id: Optional[str] = None,
    cached_config_dir: str = CACHED_CONFIG_DIR,
) -> Dict[str, Any]:
    """Builds and verifies a support bundle for the selected site model."""
    selected = _resolve_selected_site(udmi_root, site_model, site_roots)
    site_name = site_display_name(udmi_root, selected)

    active = [s for s in running_sessions if s.get("running")]
    if active:
        raise SupportBundleBusyError(
            f"Sequencer session {active[0].get('session_id')} is still running; its "
            "results and cached config are being written. Export the support bundle "
            "after the run finishes."
        )

    bundle_id = uuid.uuid4().hex
    work_dir = os.path.join(exports_root(udmi_root), bundle_id)
    os.makedirs(work_dir)
    SERVER_LOGGER.info(
        "SupportBundle", "export.start", correlation_id=correlation_id,
        details={"bundle_id": bundle_id, "site_model": selected},
    )
    filename = time.strftime("udmi-support_%Y%m%d-%H%M%S.tgz", time.gmtime())
    path = os.path.join(work_dir, filename)
    try:
        try:
            written = _write_archive(
                path + PARTIAL_SUFFIX, udmi_root, selected, site_name, cached_config_dir
            )
        except OSError as exc:
            raise SupportBundleError(
                f"Cannot read {exc.filename or 'a file'} for the support bundle: "
                f"{exc.strerror or exc}. Nothing was exported."
            ) from exc
        members = _verify(path + PARTIAL_SUFFIX, site_name)
        os.rename(path + PARTIAL_SUFFIX, path)
    except Exception:
        shutil.rmtree(work_dir, ignore_errors=True)
        raise

    SERVER_LOGGER.info(
        "SupportBundle", "export.complete", correlation_id=correlation_id,
        details={"bundle_id": bundle_id, "filename": filename, "members": len(members),
                 "excluded_secrets": len(written["excluded"])},
    )
    return {
        "bundle_id": bundle_id,
        "filename": filename,
        "size_bytes": os.path.getsize(path),
        "site_model": display(selected, udmi_root),
        "member_count": len(members),
        "excluded_secret_count": len(written["excluded"]),
        "cached_configs": written["cached_configs"],
        "download_url": f"/api/support-bundle/download?bundle_id={bundle_id}",
    }


def _raise(error: OSError) -> None:
    """os.walk otherwise skips unreadable directories silently."""
    raise error


def _add(archive: tarfile.TarFile, path: str, arcname: str) -> None:
    """Archives one filesystem entry (never recursing, never following links).

    The header is built from lstat directly rather than TarFile.add/gettarinfo,
    which resolves the owner's user and group names for every entry. Those
    names are cleared anyway, and the group lookup alone measured ~5 ms per
    call on a corporate host: a 48,854-file lab site model took 295 s instead
    of ~10 s.
    """
    st = os.lstat(path)
    info = tarfile.TarInfo(arcname)
    info.mode = stat.S_IMODE(st.st_mode)
    info.mtime = int(st.st_mtime)
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    if stat.S_ISLNK(st.st_mode):
        info.type = tarfile.SYMTYPE
        info.linkname = os.readlink(path)
        archive.addfile(info)
    elif stat.S_ISDIR(st.st_mode):
        info.type = tarfile.DIRTYPE
        archive.addfile(info)
    elif stat.S_ISREG(st.st_mode):
        info.size = st.st_size
        with open(path, "rb") as fh:
            archive.addfile(info, fh)
    else:
        raise SupportBundleError(
            f"Cannot archive {path}: it is not a regular file, directory or "
            "symlink (socket, FIFO or device). Remove it and export again."
        )


def _write_archive(path, udmi_root, site_abs, site_name, cached_config_dir) -> Dict[str, Any]:
    out_dir = os.path.join(udmi_root, OUT_MEMBER)
    cached = [name for name in CACHED_CONFIG_NAMES
              if os.path.isfile(os.path.join(cached_config_dir, name))]
    excluded: List[str] = []
    with tarfile.open(path, "w:gz", compresslevel=GZIP_LEVEL) as archive:
        _add_tree(archive, site_abs, site_member_path(site_name), excluded,
                  skip_dirs=(), skip_top_files=())
        # The cached configs are archived as out/<name>, as bin/support copies
        # them there; an out/ file of the same name is superseded by the cache.
        _add_tree(archive, out_dir, OUT_MEMBER, excluded,
                  skip_dirs=(os.path.realpath(exports_root(udmi_root)),),
                  skip_top_files=cached)
        for name in cached:
            _add(archive, os.path.join(cached_config_dir, name), f"{OUT_MEMBER}/{name}")
    return {"excluded": excluded, "cached_configs": cached}


def _add_tree(archive, source, prefix, excluded, skip_dirs, skip_top_files) -> None:
    """Adds `source` as `prefix`, skipping VCS metadata and private key files.

    Symlinks are archived as links and never followed, so nothing outside
    `source` is pulled in.
    """
    _add(archive, source, prefix)
    for dirpath, dirnames, filenames in os.walk(source, onerror=_raise):
        rel = os.path.relpath(dirpath, source)
        base = prefix if rel == "." else f"{prefix}/{rel}"
        kept = []
        for name in sorted(dirnames):
            full = os.path.join(dirpath, name)
            if name in VCS_NAMES or os.path.realpath(full) in skip_dirs:
                continue
            _add(archive, full, f"{base}/{name}")
            if not os.path.islink(full):
                kept.append(name)
        dirnames[:] = kept
        for name in sorted(filenames):
            if name in VCS_NAMES or (rel == "." and name in skip_top_files):
                continue
            member = f"{base}/{name}"
            if PRIVATE_KEY_PATTERN.search(name):
                excluded.append(member)
                continue
            _add(archive, os.path.join(dirpath, name), member)


def _verify(path: str, site_name: str) -> List[str]:
    """Re-reads the finished archive and rejects anything unsafe to share."""
    members = _list_members(path)
    leaked = [m for m in members if PRIVATE_KEY_PATTERN.search(m)]
    if leaked:
        raise SupportBundleError(
            "Private key material found in the support archive; it was deleted. "
            f"Members: {', '.join(leaked[:10])}"
        )
    site_member = site_member_path(site_name)
    stray = [m for m in members
             if m != OUT_MEMBER and not m.startswith(OUT_MEMBER + "/")
             and m != site_member and not m.startswith(site_member + "/")]
    if stray:
        raise SupportBundleError(
            "The support archive has members outside out/ and the selected site "
            f"model; it was discarded. Members: {', '.join(stray[:10])}"
        )
    if site_member not in members:
        raise SupportBundleError(
            f"The support archive does not contain the selected site model "
            f"({site_member}); it was discarded."
        )
    return members


def _list_members(path: str) -> List[str]:
    try:
        with tarfile.open(path, "r:gz") as archive:
            return [member.name.rstrip("/") for member in archive.getmembers()]
    except (tarfile.TarError, OSError) as exc:
        raise SupportBundleError(f"Cannot read support archive {path}: {exc}") from exc


def bundle_path(udmi_root: str, bundle_id: str) -> str:
    """Resolves a bundle id to its archive, rejecting anything but a real export."""
    if not bundle_id or not BUNDLE_ID_PATTERN.match(bundle_id):
        raise SupportBundleError(f"Invalid support bundle id: {bundle_id!r}")
    work_dir = os.path.join(exports_root(udmi_root), bundle_id)
    if not os.path.isdir(work_dir):
        raise SupportBundleError(f"Support bundle not found: {bundle_id}")
    archives = [n for n in os.listdir(work_dir) if ARCHIVE_NAME_PATTERN.match(n)]
    if len(archives) != 1:
        raise SupportBundleError(
            f"Support bundle not found: expected one archive in {work_dir}, "
            f"found {len(archives)}"
        )
    return os.path.join(work_dir, archives[0])
