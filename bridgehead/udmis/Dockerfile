FROM quay.io/coreos/etcd:v3.5.13 AS etcd-client-builder
FROM tomcat:11-jre21-temurin AS tomcat
FROM ghcr.io/faucetsdn/udmi:udmis-latest

RUN apk add --no-cache bash openjdk21 gcompat curl jq sudo git python3 moreutils mosquitto mosquitto-clients openssl coreutils nano telegraf openssh-client

COPY --from=etcd-client-builder /usr/local/bin/etcdctl /usr/local/bin/etcdctl
COPY --from=tomcat /usr/local/tomcat /usr/local/tomcat
COPY mosquitto/mosquitto_ctrl.sh /root/udmi/etc/
COPY udmis/startup/udmis_startup.sh /usr/local/bin/startup/
COPY udmis/startup/telegraf.conf /usr/local/bin/startup/
COPY udmis/bridgeheadManager.war /usr/local/tomcat/webapps/

RUN chmod +x /root/udmi/etc/mosquitto_ctrl.sh
RUN chmod +x /usr/local/bin/startup/udmis_startup.sh
RUN chmod +x /usr/local/bin/startup/telegraf.conf 
RUN chmod +x /usr/local/bin/startup/telegraf.conf 

CMD ["/usr/local/bin/startup/udmis_startup.sh"]