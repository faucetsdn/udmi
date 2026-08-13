import time
import asyncio
from typing import Optional

class RateLimitTimeoutError(Exception):
    """Raised when waiting to acquire a rate-limiting token exceeds the allowed timebudget."""
    pass

class AsyncRateLimiter:
    """
    Thread-safe, async-first Token Bucket rate limiter.
    Ensures outbound API request rates remain within GenAI quota guidelines.
    """

    def __init__(self, max_requests: int, time_period_seconds: float):
        self.capacity = float(max_requests)
        self.refill_rate = float(max_requests) / time_period_seconds
        self.tokens = float(max_requests)
        self.last_update = time.monotonic()
        self.lock = asyncio.Lock()

    async def acquire(self, timeout_seconds: Optional[float] = None) -> bool:
        """
        Attempts to acquire a token from the bucket.
        If empty, it sleeps until refilled. If timeout_seconds is reached,
        returns False (or raises RateLimitTimeoutError depending on caller preference).
        """
        start_time = time.monotonic()
        
        while True:
            async with self.lock:
                now = time.monotonic()
                elapsed = now - self.last_update
                self.last_update = now
                
                # Refill tokens based on elapsed time
                self.tokens = min(self.capacity, self.tokens + (elapsed * self.refill_rate))
                
                if self.tokens >= 1.0:
                    self.tokens -= 1.0
                    return True
                
                # Calculate wait duration until next token is available
                needed = 1.0 - self.tokens
                wait_time = needed / self.refill_rate

            # Check if wait time exceeds remaining timeout budget
            if timeout_seconds is not None:
                elapsed_total = time.monotonic() - start_time
                remaining_timeout = timeout_seconds - elapsed_total
                if wait_time > remaining_timeout:
                    # Let the caller fail-open by returning False
                    return False
            
            # Sleep until next token refilled (or sleep a small fraction of time)
            await asyncio.sleep(min(wait_time, 0.1))
