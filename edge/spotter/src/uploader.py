import logging
import requests
from typing import Iterator

LOGGER = logging.getLogger("spotter_uploader")

class ResumableUploader:
    def __init__(self, init_url: str):
        self.init_url = init_url
        self.session_uri = None

    def initiate_session(self) -> str:
        """Initiates a resumable upload session with GCS."""
        LOGGER.info("Initiating GCS Resumable Upload session...")
        headers = {
            "x-goog-resumable": "start",
            "Content-Type": "application/octet-stream"
        }
        # Perform empty POST to get the session URI. 
        # We use a tuple timeout (connect, read) to avoid blocking indefinitely.
        response = requests.post(self.init_url, headers=headers, timeout=(10, 30))
        response.raise_for_status()
        
        if response.status_code in (200, 201):
            # GCS returns Location header containing the session URI
            self.session_uri = response.headers.get("Location")
            if not self.session_uri:
                raise RuntimeError("Location header missing in GCS initialization response")
            LOGGER.info("GCS session initiated. Session URI: %s", self.session_uri)
            return self.session_uri
        else:
            raise RuntimeError(f"Unexpected response status initiating session: {response.status_code}")

    def upload_stream(self, data_generator: Iterator[bytes]) -> None:
        """Streams data from the generator directly to the GCS Session URI."""
        if not self.session_uri:
            self.initiate_session()
            
        LOGGER.info("Starting chunked streaming upload to GCS...")
        headers = {
            "Content-Type": "application/octet-stream"
        }
        # Passing an iterator to data tells requests to use chunked transfer encoding.
        response = requests.put(self.session_uri, data=data_generator, headers=headers, timeout=(10, 300))
        response.raise_for_status()
        LOGGER.info("GCS upload completed successfully.")
