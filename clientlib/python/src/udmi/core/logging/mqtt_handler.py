"""
Implement UDMIMqttLogHandler to redirect standard Python logs to the
UDMI 'events/system' topic.

It may be useful for remote diagnostics and audit trails (status tracking).
"""

import logging
from datetime import datetime
from datetime import timezone
from typing import TYPE_CHECKING

from udmi.constants import UDMI_VERSION
from udmi.schema import Entry
from udmi.schema import SystemEvents

if TYPE_CHECKING:
    from udmi.core.managers import SystemManager


class UDMIMqttLogHandler(logging.Handler):
    """
    A custom logging handler that publishes Python log records as UDMI
    SystemEvents.
    """

    def __init__(self, system_manager: "SystemManager"):
        """
        Initializes the handler.

        Args:
            system_manager: The instance of SystemManager used to publish events.
        """
        super().__init__()
        self.system_manager = system_manager

    def emit(self, record: logging.LogRecord) -> None:
        """
        Captures a log record, formats it as a UDMI Entry, and publishes it.
        """
        try:
            message = self.format(record)

            # Map Python level to UDMI level (approximate mapping: x10)
            # DEBUG(10)->100, INFO(20)->200, WARNING(30)->300, ERROR(40)->400,
            # CRITICAL(50)->500
            udmi_level = record.levelno * 10

            timestamp = datetime.fromtimestamp(record.created,
                                               tz=timezone.utc).isoformat()

            log_entry = Entry(
                message=message,
                level=udmi_level,
                timestamp=timestamp
            )

            system_event = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                logentries=[log_entry]
            )

            self.system_manager.publish_event(system_event, "system")
        except Exception:  # pylint: disable=broad-except
            self.handleError(record)
