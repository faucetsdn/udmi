import logging
import subprocess
import time
from typing import Iterator

LOGGER = logging.getLogger("spotter_pcap")

def capture_packets(
    interface: str,
    filter_str: str,
    max_duration_sec: int,
    max_bytes: int
) -> Iterator[bytes]:
    """Spawns tcpdump to capture packets, yielding chunks of bytes.
    
    Guarantees resource safety by terminating tcpdump when limits are exceeded.
    """
    LOGGER.info(
        "Starting packet capture. Interface: %s, Filter: '%s', Duration Limit: %ss, Size Limit: %s bytes",
        interface, filter_str, max_duration_sec, max_bytes
    )
    
    cmd = ["tcpdump", "-i", interface, "-w", "-", "-U"]
    if filter_str:
        cmd.append(filter_str)
        
    LOGGER.info("Executing cmd: %s", " ".join(cmd))
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0  # Unbuffered reads
    )
    
    start_time = time.time()
    total_bytes = 0
    
    try:
        while True:
            # Check elapsed time
            elapsed = time.time() - start_time
            if elapsed >= max_duration_sec:
                LOGGER.info("Capture duration limit reached (%ss). Stopping tcpdump...", max_duration_sec)
                break
                
            # Read a chunk from stdout (non-blocking style read using read1 or standard read block)
            # Since stdout is a pipe, read(65536) blocks until some bytes are available
            # We can use a short timeout or read blocking since tcpdump writes immediately with -U
            chunk = proc.stdout.read(65536)
            if not chunk:
                # Process finished or closed
                if proc.poll() is not None:
                    break
                time.sleep(0.1)
                continue
                
            total_bytes += len(chunk)
            yield chunk
            
            if total_bytes >= max_bytes:
                LOGGER.warning("Capture size limit reached (%s bytes). Stopping tcpdump...", max_bytes)
                break
                
    finally:
        # Clean shutdown of subprocess
        if proc.poll() is None:
            LOGGER.info("Terminating tcpdump process...")
            proc.terminate()
            try:
                # Wait up to 3 seconds for graceful exit
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                LOGGER.warning("tcpdump did not exit on SIGTERM. Killing...")
                proc.kill()
                proc.wait()
                
        # Check for error logs in stderr
        stderr_data = proc.stderr.read()
        if stderr_data:
            LOGGER.debug("tcpdump stderr: %s", stderr_data.decode(errors='replace'))
            
        LOGGER.info("Packet capture worker stopped. Total bytes captured: %s", total_bytes)
