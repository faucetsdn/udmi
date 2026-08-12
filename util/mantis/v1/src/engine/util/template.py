import re

# Match common timestamp formats
TIMESTAMP_PATTERN = re.compile(
    r'\b\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?\b|\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b'
)
# Match hex addresses (e.g. 0x7ffd2f, 0x1a2b3c)
HEX_PATTERN = re.compile(r'\b0x[0-9a-fA-F]+\b')
# Match UUIDs
UUID_PATTERN = re.compile(r'\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b')
# Match integers and decimal numbers
NUMBER_PATTERN = re.compile(r'\b\d+(?:\.\d+)?\b')

def normalize_log_text(text: str) -> str:
    """
    Strips dynamic variables (timestamps, hex addresses, UUIDs, numeric ids) from logs
    to allow consistent semantic similarity match calculation.
    """
    if not text:
        return ""

    # Replace timestamps
    text = TIMESTAMP_PATTERN.sub("<TIMESTAMP>", text)
    # Replace hex addresses
    text = HEX_PATTERN.sub("<HEX>", text)
    # Replace UUIDs
    text = UUID_PATTERN.sub("<UUID>", text)
    # Replace normal numbers (while keeping characters)
    text = NUMBER_PATTERN.sub("<NUM>", text)

    return text
