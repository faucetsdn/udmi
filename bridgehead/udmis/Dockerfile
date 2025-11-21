FROM quay.io/coreos/etcd:v3.5.13 AS etcd-client-builder
FROM ghcr.io/faucetsdn/udmi:udmis-latest

RUN apk add --no-cache bash openjdk21 gcompat curl jq sudo git python3 moreutils mosquitto mosquitto-clients openssl coreutils nano

COPY --from=etcd-client-builder /usr/local/bin/etcdctl /usr/local/bin/etcdctl
COPY startup/mosquitto_ctrl.sh /root/udmi/etc/
COPY startup/udmis_startup.sh /usr/local/bin/startup/

RUN chmod +x /usr/local/bin/startup/udmis_startup.sh
RUN chmod +x /root/udmi/etc/mosquitto_ctrl.sh

CMD ["/usr/local/bin/startup/udmis_startup.sh"]
