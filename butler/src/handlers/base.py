"""Base message handler interface for Butler."""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional

from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager


class BaseHandler(ABC):
    """Abstract base class for all Butler message handlers."""

    @abstractmethod
    def can_handle(self, sub_folder: Optional[str], sub_type: Optional[str], payload: Dict[str, Any]) -> bool:
        """Returns True if this handler can process the message."""

    @abstractmethod
    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        """Processes the message and writes records to database.

        Returns:
            Dict containing processing result summary (e.g. {'target': 'postgres', 'table': ..., 'count': ...}).
        """
