# Single-process container for UDMI Spotter Unified Edge Node

# --- Stage 1: Build Spotter virtual environment ---
FROM debian:12-slim AS build-spotter
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes \
    python3-venv gcc libpython3-dev && \
    python3 -m venv /venv && \
    /venv/bin/pip install --upgrade pip setuptools wheel

COPY edge/spotter/requirements.txt /requirements.txt
RUN /venv/bin/pip install --disable-pip-version-check -r /requirements.txt

COPY clientlib/python /app/clientlib
COPY gencode/python/udmi/schema /app/gencode_schema
RUN rm -rf /app/clientlib/src/udmi/schema && cp -r /app/gencode_schema /app/clientlib/src/udmi/schema
RUN /venv/bin/pip install --disable-pip-version-check /app/clientlib

# --- Stage 2: Final Runtime ---
FROM debian:12-slim
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes \
    python3 \
    coreutils \
    nmap \
    iputils-ping \
    tcpdump \
    libcap2-bin \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Copy virtual environment
COPY --from=build-spotter /venv /venv
ENV PATH="/venv/bin:$PATH"
ENV PYTHONPATH="/app/spotter:/app/clientlib/src"

# Create application and logging directories
RUN mkdir -p /app/spotter /var/log/spotter /config

# Copy application files
COPY clientlib/python /app/clientlib
COPY edge/spotter/src /app/spotter

# Set capabilities for raw socket packet captures
RUN setcap cap_net_raw,cap_net_admin=eip $(which tcpdump || echo /usr/bin/tcpdump) || true

WORKDIR /app
ENTRYPOINT ["python3", "/app/spotter/agent.py"]
