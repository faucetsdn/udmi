import abc
from typing import Any, Callable


class Publisher(abc.ABC):

  def set_config_callback(self, func: Callable[[str], None]) -> None:
    """Set callback to receive config messgaes on

    Args: func Function with signature (str) to recieve config
    """
    pass

  def publish_message(self, topic: str, payload: str) -> Any:
    """Publish a message"""
    pass
