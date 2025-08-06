FROM debian:12-slim AS build
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes python3-venv gcc libpython3-dev && \
    python3 -m venv /venv && \
    /venv/bin/pip install --upgrade pip setuptools wheel

# Build the virtualenv as a separate step: Only re-execute this step when requirements.txt changes
FROM build AS build-venv
COPY requirements.txt /requirements.txt
RUN /venv/bin/pip install --disable-pip-version-check -r /requirements.txt
RUN sed -i 's#for each in range(100):#for each in range(13):#' /venv/lib/python3.11/site-packages/BAC0/core/functions/Discover.py
RUN sed -i 's#time.sleep(1 / 1000)#time.sleep(1 / 10)#' /venv/lib/python3.11/site-packages/BAC0/core/functions/Discover.py

FROM debian:12-slim
RUN apt-get update && \
    apt-get install --no-install-suggests --no-install-recommends --yes python3 coreutils nmap iputils-ping
COPY --from=build-venv /venv /venv
COPY . /app
WORKDIR /app
ENTRYPOINT ["/venv/bin/python3", "main.py"]