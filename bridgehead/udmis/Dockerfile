ARG UDMIS_BASE_IMAGE=ghcr.io/faucetsdn/udmi:udmis-latest
FROM quay.io/coreos/etcd:v3.5.13 AS etcd-client-builder
FROM tomcat:11-jre21-temurin AS tomcat
FROM ${UDMIS_BASE_IMAGE}

RUN apk add --no-cache bash openjdk21 gcompat curl jq sudo git python3 moreutils mosquitto mosquitto-clients openssl coreutils nano telegraf openssh-client

COPY --from=etcd-client-builder /usr/local/bin/etcdctl /usr/local/bin/etcdctl
COPY --from=tomcat /usr/local/tomcat /usr/local/tomcat
COPY mosquitto/mosquitto_ctrl.sh /root/udmi/etc/
COPY udmis/startup/udmis_startup.sh /usr/local/bin/startup/
COPY udmis/startup/telegraf.conf /usr/local/bin/startup/
COPY udmis/UI/ManagementWebapp /tmp/ManagementWebapp
RUN rm -rf /tmp/ManagementWebapp/webapp/WEB-INF/classes/webapp && \
    mkdir -p /tmp/ManagementWebapp/webapp/WEB-INF/classes && \
    javac -cp "/tmp/ManagementWebapp/webapp/WEB-INF/lib/*" \
      -d /tmp/ManagementWebapp/webapp/WEB-INF/classes \
      /tmp/ManagementWebapp/src/webapp/*.java \
      /tmp/ManagementWebapp/src/webapp/mqtt/*.java && \
    jar -cf /usr/local/tomcat/webapps/bridgeheadManager.war -C /tmp/ManagementWebapp/webapp . && \
    rm -rf /tmp/ManagementWebapp

RUN chmod +x /root/udmi/etc/mosquitto_ctrl.sh
RUN chmod +x /usr/local/bin/startup/udmis_startup.sh
RUN chmod +x /usr/local/bin/startup/telegraf.conf

CMD ["/usr/local/bin/startup/udmis_startup.sh"]