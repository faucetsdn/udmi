"""Message dispatcher for routing incoming UDMI messages to appropriate handlers."""

from typing import Any, Dict, List, Optional
from butler.src.handlers.alarms import AlarmsHandler
from butler.src.handlers.base import BaseHandler
from butler.src.handlers.discovery import DiscoveryHandler
from butler.src.handlers.metadata import MetadataHandler
from butler.src.handlers.point_state import PointStateHandler
from butler.src.handlers.pointset_events import PointsetEventsHandler
from butler.src.handlers.raw_fallback import RawFallbackHandler
from butler.src.handlers.system_state import SystemStateHandler
from butler.src.handlers.validation import ValidationHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class MessageDispatcher:
    """Dispatches UDMI messages to registered database handlers."""

    def __init__(
        self,
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
        custom_handlers: Optional[List[BaseHandler]] = None,
        always_save_raw: bool = True,
    ):
        self.postgres_manager = postgres_manager
        self.influx_manager = influx_manager
        self.always_save_raw = always_save_raw
        self.raw_fallback = RawFallbackHandler()

        if custom_handlers is not None:
            self.handlers = custom_handlers
        else:
            self.handlers = [
                PointsetEventsHandler(),
                PointStateHandler(),
                SystemStateHandler(),
                DiscoveryHandler(),
                ValidationHandler(),
                AlarmsHandler(),
                MetadataHandler(),
                self.raw_fallback,
            ]

    def dispatch(self, envelope: Dict[str, Any], payload: Dict[str, Any]) -> Dict[str, Any]:
        """Routes a message to the first matching handler.

        Args:
            envelope: Envelope metadata dictionary.
            payload: Payload dictionary or data object.

        Returns:
            Dict containing processing result summary.
        """
        sub_folder = envelope.get("subFolder")
        sub_type = envelope.get("subType")

        matched_handler = None
        for handler in self.handlers:
            if handler.can_handle(sub_folder, sub_type, payload):
                matched_handler = handler
                break

        if matched_handler is None:
            matched_handler = self.raw_fallback

        result = matched_handler.process_message(
            envelope, payload, self.postgres_manager, self.influx_manager
        )

        if self.always_save_raw and matched_handler is not self.raw_fallback:
            self.raw_fallback.process_message(
                envelope, payload, self.postgres_manager, self.influx_manager
            )

        return result
