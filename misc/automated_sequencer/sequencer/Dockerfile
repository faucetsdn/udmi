FROM eu.gcr.io/gke-release/debian-base:latest
ARG DEBIAN_FRONTEND=noninteractive
RUN mkdir -p /etc/ssl/certs/java/

# Fix for installing java but we also need bash anyway as a dependancy of the UDMI tooling
# https://groups.google.com/g/linux.debian.bugs.dist/c/2zq4rjzg7Fs
RUN apt update && apt install -y bash

RUN apt update && apt install -y --allow-change-held-packages jq git openjdk-17-jdk moreutils curl sudo gnupg libcap2 hxtools
RUN apt update && apt install -y --allow-change-held-packages python3 python3-pip python3-venv
RUN echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg  add - && apt-get update -y && apt-get install google-cloud-cli -y

COPY entrypoint.sh /
COPY config.sh /
ENTRYPOINT ["/entrypoint.sh"]
