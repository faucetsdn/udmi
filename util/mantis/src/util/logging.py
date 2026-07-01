# Project Mantis - Shared Logging Utilities
import sys

class Tee:
    """Dual-output stream to print to stdout/stderr and write persistently to a log file."""

    def __init__(self, original_stream, filepath):
        self.stream = original_stream
        self.file = open(filepath, 'a', encoding='utf-8')

    def write(self, data):
        self.stream.write(data)
        self.file.write(data)
        self.file.flush()

    def flush(self):
        self.stream.flush()
        self.file.flush()

    def __getattr__(self, name):
        return getattr(self.stream, name)
