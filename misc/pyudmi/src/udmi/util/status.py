from enum import StrEnum


class Status(StrEnum):
    """
    Generic enum used for tracking status for any service/tasks.
    """
    INITIALIZED = "initialized"  # service initialized but not doing anything
    PENDING = "pending"  # scheduled in the future
    STARTING = "starting"  # received the signal to start
    STARTED = "started"  # has started
    CANCELLING = "cancelling"  # received the signal to cancel
    CANCELLED = "cancelled"  # has been cancelled
    FINISHED = "finished"  # task has finished
    SCHEDULED = "scheduled"  # task is scheduled
    ERROR = "error"  # some error occurred while processing
