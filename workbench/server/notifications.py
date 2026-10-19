"""Email notifications for finished Mantis answers and sequencer runs (Layer 4).

Delivery uses the Gmail API with the operator's own Application Default
Credentials, and the only recipient is the address those credentials belong to.
Nothing is sent until the operator has consented in the Settings panel; the
consent record is persisted with the other Workbench settings, outside the
repository, in the same `~/.config/udmi/workbench.json` document the site-root
registry uses (under its own `notifications` key).

Consent is bound to an address. If the credentials later belong to a different
account, delivery is refused until the operator consents again, so a changed
login can never redirect results to someone else's inbox.

Every delivery attempt is recorded (SENDING, SENT, FAILED with the reason) and
exposed through `GET /api/notifications`, so a failed email is visible in the
Settings panel rather than lost in a background thread.
"""

import base64
import json
import os
import threading
import uuid
from datetime import datetime, timezone
from email.mime.application import MIMEApplication
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Any, Callable, Dict, List, Optional, Tuple

from workbench.server.logger import SERVER_LOGGER
from workbench.server.site_roots import default_config_path

CONFIG_KEY = "notifications"
GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
SETUP_COMMAND = (
    "gcloud auth application-default login --scopes="
    "https://www.googleapis.com/auth/cloud-platform,"
    f"{GMAIL_SEND_SCOPE},openid,https://www.googleapis.com/auth/userinfo.email"
)
MAX_DELIVERIES = 20
SUBJECT_PREFIX = "[UDMI Workbench]"


class NotificationError(Exception):
    """Raised when consent, credentials, or delivery is not usable."""


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class Email:
    """A composed message: HTML body, inline PNG images by content id, attachments."""

    def __init__(
        self,
        subject: str,
        html: str,
        text: str,
        inline_images: Optional[Dict[str, bytes]] = None,
        attachments: Optional[List[Tuple[str, bytes, str]]] = None,
    ):
        self.subject = subject
        self.html = html
        self.text = text
        self.inline_images = inline_images or {}
        self.attachments = attachments or []

    def to_mime(self, address: str) -> MIMEMultipart:
        """Builds multipart/mixed( multipart/related( alternative(text, html), images ), files )."""
        root = MIMEMultipart("mixed")
        root["To"] = address
        root["From"] = address
        root["Subject"] = f"{SUBJECT_PREFIX} {self.subject}"

        related = MIMEMultipart("related")
        alternative = MIMEMultipart("alternative")
        alternative.attach(MIMEText(self.text, "plain", "utf-8"))
        alternative.attach(MIMEText(self.html, "html", "utf-8"))
        related.attach(alternative)
        for content_id, png in self.inline_images.items():
            image = MIMEImage(png, "png")
            image.add_header("Content-ID", f"<{content_id}>")
            image.add_header("Content-Disposition", "inline", filename=f"{content_id}.png")
            related.attach(image)
        root.attach(related)

        for filename, content, subtype in self.attachments:
            part = MIMEApplication(content, subtype)
            part.add_header("Content-Disposition", "attachment", filename=filename)
            root.attach(part)
        return root


def _adc_identity() -> Tuple[Any, str, List[str]]:
    """Returns (credentials, email, scopes) of the current Application Default Credentials."""
    try:
        import google.auth
        import google.auth.transport.requests
        import requests
    except ImportError as exc:
        raise NotificationError(
            f"google-auth is not installed ({exc}); run bin/setup_base to install etc/requirements.txt."
        ) from exc
    try:
        credentials, _ = google.auth.default()
        credentials.refresh(google.auth.transport.requests.Request())
    except Exception as exc:  # google.auth raises several unrelated types
        raise NotificationError(
            f"Application Default Credentials are unusable ({exc.__class__.__name__}: {exc}). "
            f"Run: {SETUP_COMMAND}"
        ) from exc
    response = requests.get(
        "https://oauth2.googleapis.com/tokeninfo",
        params={"access_token": credentials.token},
        timeout=15,
    )
    if response.status_code != 200:
        raise NotificationError(
            f"Could not inspect the credentials' token (HTTP {response.status_code}: "
            f"{response.text[:200]}). Run: {SETUP_COMMAND}"
        )
    info = response.json()
    email = info.get("email")
    scopes = (info.get("scope") or "").split()
    if not email:
        raise NotificationError(
            "The Application Default Credentials carry no email address, so there is no "
            f"inbox to deliver to. Run: {SETUP_COMMAND}"
        )
    if email.endswith(".gserviceaccount.com"):
        raise NotificationError(
            f"The Application Default Credentials belong to the service account {email}, "
            "which has no inbox. Sign in with your own account. Run: " + SETUP_COMMAND
        )
    return credentials, email, scopes


class Notifier:
    """Consent record, identity checks, and background Gmail delivery."""

    def __init__(
        self,
        config_path: Optional[str] = None,
        identity: Callable[[], Tuple[Any, str, List[str]]] = _adc_identity,
        transport: Optional[Callable[[Any, Dict[str, str]], str]] = None,
    ):
        self.config_path = config_path or default_config_path()
        self._identity = identity
        self._transport = transport or self._gmail_send
        self._lock = threading.Lock()
        self._deliveries: List[Dict[str, Any]] = []

    # ------------------------------------------------------------ storage ---
    def _read_document(self) -> Dict[str, Any]:
        if not os.path.isfile(self.config_path):
            return {}
        with open(self.config_path, "r", encoding="utf-8") as fh:
            try:
                document = json.load(fh)
            except json.JSONDecodeError as exc:
                raise NotificationError(
                    f"Workbench settings file {self.config_path} is not valid JSON: {exc}"
                ) from exc
        if not isinstance(document, dict):
            raise NotificationError(f"Workbench settings file {self.config_path} is not a JSON object.")
        return document

    def _write_email_record(self, record: Optional[Dict[str, Any]]) -> None:
        # Read-modify-write of the shared document keeps the site-root registry's key intact.
        document = self._read_document()
        section = document.setdefault(CONFIG_KEY, {})
        if record is None:
            section.pop("email", None)
        else:
            section["email"] = record
        os.makedirs(os.path.dirname(self.config_path), exist_ok=True)
        temp_path = f"{self.config_path}.tmp"
        with open(temp_path, "w", encoding="utf-8") as fh:
            json.dump(document, fh, indent=2, sort_keys=True)
            fh.write("\n")
        os.replace(temp_path, self.config_path)

    def _email_record(self) -> Optional[Dict[str, Any]]:
        return (self._read_document().get(CONFIG_KEY) or {}).get("email")

    # ------------------------------------------------------------- status ---
    def describe(self) -> Dict[str, Any]:
        """Consent state, whether the current credentials can send, and recent deliveries."""
        record = self._email_record()
        address, scope_ok, problem = None, False, None
        try:
            _, address, scopes = self._identity()
            scope_ok = GMAIL_SEND_SCOPE in scopes
            if not scope_ok:
                problem = (
                    f"The credentials for {address} lack the gmail.send scope. Run: {SETUP_COMMAND}"
                )
            elif record and record.get("address") != address:
                problem = (
                    f"Consent was given for {record.get('address')}, but the credentials now "
                    f"belong to {address}. Enable email notifications again to consent for {address}."
                )
        except NotificationError as exc:
            problem = str(exc)
        with self._lock:
            deliveries = [dict(d) for d in reversed(self._deliveries)]
        return {
            "email": {
                "consented": bool(record),
                "consented_address": record.get("address") if record else None,
                "consented_at": record.get("consented_at") if record else None,
                "address": address,
                "scope_ok": scope_ok,
                "ready": bool(record) and problem is None,
                "problem": problem,
                "setup_command": SETUP_COMMAND,
            },
            "deliveries": deliveries,
        }

    def set_consent(self, consent: bool, correlation_id: Optional[str] = None) -> Dict[str, Any]:
        """Records or withdraws consent. Consent is refused unless sending is possible now."""
        if consent:
            _, address, scopes = self._identity()
            if GMAIL_SEND_SCOPE not in scopes:
                raise NotificationError(
                    f"Cannot enable email notifications: the credentials for {address} lack the "
                    f"gmail.send scope. Run: {SETUP_COMMAND}"
                )
            self._write_email_record({"address": address, "consented_at": _now()})
        else:
            self._write_email_record(None)
        SERVER_LOGGER.info(
            "Notifier",
            "consent.set",
            correlation_id=correlation_id,
            details={"consent": consent},
        )
        return self.describe()

    def require_ready(self) -> str:
        """Returns the consented address, or raises with the reason delivery is impossible."""
        record = self._email_record()
        if not record:
            raise NotificationError(
                "Email notifications are not enabled. Open Settings in the Workbench header "
                "and enable them first."
            )
        _, address, scopes = self._identity()
        if GMAIL_SEND_SCOPE not in scopes:
            raise NotificationError(
                f"The credentials for {address} lack the gmail.send scope. Run: {SETUP_COMMAND}"
            )
        if record.get("address") != address:
            raise NotificationError(
                f"Email consent was given for {record.get('address')}, but the credentials now "
                f"belong to {address}. Enable email notifications again in Settings."
            )
        return address

    # ----------------------------------------------------------- delivery ---
    def send_now(self, kind: str, email: Email, correlation_id: Optional[str] = None) -> Dict[str, Any]:
        """Sends synchronously and records the outcome. Raises NotificationError on failure."""
        delivery = {
            "id": uuid.uuid4().hex[:12],
            "kind": kind,
            "subject": email.subject,
            "status": "SENDING",
            "error": None,
            "message_id": None,
            "at": _now(),
        }
        with self._lock:
            self._deliveries.append(delivery)
            del self._deliveries[:-MAX_DELIVERIES]
        try:
            address = self.require_ready()
            credentials, _, _ = self._identity()
            raw = base64.urlsafe_b64encode(email.to_mime(address).as_bytes()).decode()
            message_id = self._transport(credentials, {"raw": raw})
        except Exception as exc:
            with self._lock:
                delivery.update(status="FAILED", error=f"{exc.__class__.__name__}: {exc}")
            SERVER_LOGGER.error(
                "Notifier",
                "delivery.failed",
                correlation_id=correlation_id,
                details={"kind": kind, "subject": email.subject},
                error={"code": "NOTIFY", "message": str(exc)},
            )
            if isinstance(exc, NotificationError):
                raise
            raise NotificationError(f"Gmail delivery failed: {exc}") from exc
        with self._lock:
            delivery.update(status="SENT", message_id=message_id, address=address)
        SERVER_LOGGER.info(
            "Notifier",
            "delivery.sent",
            correlation_id=correlation_id,
            details={"kind": kind, "subject": email.subject, "messageId": message_id},
        )
        return dict(delivery)

    def send_in_background(
        self, kind: str, compose: Callable[[], Email], correlation_id: Optional[str] = None
    ) -> None:
        """Composes and sends on a daemon thread; the outcome lands in the delivery log."""

        def deliver() -> None:
            try:
                email = compose()
            except Exception as exc:
                with self._lock:
                    self._deliveries.append({
                        "id": uuid.uuid4().hex[:12],
                        "kind": kind,
                        "subject": None,
                        "status": "FAILED",
                        "error": f"Composing the notification failed: {exc.__class__.__name__}: {exc}",
                        "message_id": None,
                        "at": _now(),
                    })
                    del self._deliveries[:-MAX_DELIVERIES]
                SERVER_LOGGER.error(
                    "Notifier",
                    "compose.failed",
                    correlation_id=correlation_id,
                    details={"kind": kind},
                    error={"code": "NOTIFY", "message": str(exc)},
                )
                return
            try:
                self.send_now(kind, email, correlation_id=correlation_id)
            except NotificationError:
                pass  # recorded in the delivery log and the server log by send_now

        threading.Thread(target=deliver, name=f"notify-{kind}", daemon=True).start()

    @staticmethod
    def _gmail_send(credentials: Any, body: Dict[str, str]) -> str:
        from googleapiclient.discovery import build

        service = build("gmail", "v1", credentials=credentials, cache_discovery=False)
        result = service.users().messages().send(userId="me", body=body).execute()
        return result.get("id")
