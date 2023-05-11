FROM haproxytech/haproxy-ubuntu:2.8
# https://github.com/haproxytech/haproxy-docker-ubuntu/blob/main/2.8/Dockerfile
COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg

RUN mkdir /run/haproxy \
    && chown haproxy:haproxy /run/haproxy \
    && chmod 2775 /run/haproxy

RUN apt update && apt install -y jq curl

COPY haproxy-entrypoint.sh /

ENTRYPOINT ["/haproxy-entrypoint.sh"]
