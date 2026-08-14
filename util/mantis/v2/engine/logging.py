import io
import logging
import sys
from typing import Optional, TextIO

# Define standard ANSI escape codes for terminal styling
COLOR_RESET = "\033[0m"
COLOR_BOLD = "\033[1m"
COLOR_GREY = "\033[90m"
COLOR_RED = "\033[91m"
COLOR_GREEN = "\033[92m"
COLOR_YELLOW = "\033[93m"
COLOR_BLUE = "\033[94m"
COLOR_MAGENTA = "\033[95m"
COLOR_CYAN = "\033[96m"


class ColoredFormatter(logging.Formatter):
    """Custom logging formatter providing professional colored terminal outputs."""
    
    COLORS = {
        logging.DEBUG: COLOR_GREY,
        logging.INFO: COLOR_CYAN,
        logging.WARNING: COLOR_YELLOW,
        logging.ERROR: COLOR_RED,
        logging.CRITICAL: COLOR_RED + COLOR_BOLD,
    }

    def format(self, record: logging.LogRecord) -> str:
        color = self.COLORS.get(record.levelno, COLOR_RESET)
        level_name = f"{color}{record.levelname:<8}{COLOR_RESET}"
        
        timestamp = self.formatTime(record, self.datefmt)
        msg = record.getMessage()
        
        if record.levelno >= logging.WARNING:
            loc = f"{COLOR_GREY}[{record.filename}:{record.lineno}]{COLOR_RESET}"
            return f"[{timestamp}] {level_name} {loc} {msg}"
        else:
            return f"[{timestamp}] {level_name} {msg}"


class Tee:
    """Dual-output stream to print to stdout/stderr and write persistently to a log file."""

    def __init__(self, original_stream: TextIO, filepath: str):
        self.stream = original_stream
        self.file = open(filepath, 'a', encoding='utf-8')

    def write(self, data: str) -> None:
        self.stream.write(data)
        self.file.write(data)
        self.file.flush()

    def flush(self) -> None:
        self.stream.flush()
        self.file.flush()

    def __getattr__(self, name):
        return getattr(self.stream, name)


def setup_logging(
    verbose: bool = False, 
    log_filepath: Optional[str] = None
) -> logging.Logger:
    """Initializes and returns the standard logger instance for the Mantis package."""
    logger = logging.getLogger("mantis")
    logger.setLevel(logging.DEBUG if verbose else logging.INFO)
    logger.handlers.clear()

    # Console Handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_formatter = ColoredFormatter(datefmt="%H:%M:%S")
    console_handler.setFormatter(console_formatter)
    logger.addHandler(console_handler)

    # Persistent File Handler
    if log_filepath:
        file_handler = logging.FileHandler(log_filepath, encoding="utf-8")
        file_formatter = logging.Formatter(
            "[%(asctime)s] %(levelname)-8s [%(filename)s:%(lineno)d]: %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S"
        )
        file_handler.setFormatter(file_formatter)
        file_handler.setLevel(logging.DEBUG)
        logger.addHandler(file_handler)

    return logger


def get_logger() -> logging.Logger:
    """Retrieves the configured logger instance for the Mantis package."""
    return logging.getLogger("mantis")
