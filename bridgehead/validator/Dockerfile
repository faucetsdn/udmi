FROM ghcr.io/faucetsdn/udmi:validator-latest

COPY startup.sh /usr/local/bin/
COPY discovery.sh /root/discovery.sh

RUN apk add --no-cache openssh
RUN chmod +x /usr/local/bin/startup.sh
RUN chmod +x /root/discovery.sh

CMD ["/usr/local/bin/startup.sh"]