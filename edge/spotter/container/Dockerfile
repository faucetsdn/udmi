# Dual-process container for UDMI Spotter Node

# --- Stage 1: Build legacy virtualenv ---
FROM debian:12-slim AS build-legacy
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes python3-venv gcc libpython3-dev && \
    python3 -m venv /venv_legacy && \
    /venv_legacy/bin/pip install --upgrade pip setuptools wheel
COPY misc/discoverynode/src/requirements.txt /requirements_legacy.txt
RUN /venv_legacy/bin/pip install --disable-pip-version-check -r /requirements_legacy.txt

# --- Stage 2: Build spotter virtualenv ---
FROM debian:12-slim AS build-spotter
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes python3-venv gcc libpython3-dev && \
    python3 -m venv /venv_spotter && \
    /venv_spotter/bin/pip install --upgrade pip setuptools wheel
# Copy clientlib pyproject.toml first to cache dependencies installation
COPY clientlib/python /app/clientlib
COPY gencode/python/udmi/schema /app/gencode_schema
RUN rm -f /app/clientlib/src/udmi/schema && cp -r /app/gencode_schema /app/clientlib/src/udmi/schema
RUN /venv_spotter/bin/pip install --disable-pip-version-check /app/clientlib


# --- Stage 3: Final Runtime ---
FROM debian:12-slim
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes \
    python3 \
    coreutils \
    nmap \
    iputils-ping \
    tcpdump \
    libcap2-bin \
    && rm -rf /var/lib/apt/lists/*

# Copy virtual environments
COPY --from=build-legacy /venv_legacy /venv_legacy
COPY --from=build-spotter /venv_spotter /venv_spotter

# Create directories for app files, configuration, and logs
RUN mkdir -p /app/legacy /app/spotter /var/log/discovery_node /var/log/spotter /config

# Copy application files
COPY misc/discoverynode/src /app/legacy
COPY clientlib/python /app/clientlib
COPY edge/spotter/src /app/spotter
COPY edge/spotter/container/supervisor.sh /app/supervisor.sh

RUN chmod +x /app/supervisor.sh

# Set capability to allow tcpdump to capture packets without root (needed for Phase 2 PCAP)
# This is a good practice to set up early.
RUN setcap cap_net_raw,cap_net_admin=eip /usr/sbin/tcpdump || true

WORKDIR /app
ENTRYPOINT ["/bin/bash", "/app/supervisor.sh"]
