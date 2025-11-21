FROM ghcr.io/faucetsdn/udmi:udmis-latest AS udmi 
FROM eclipse-mosquitto:latest

RUN apk add --no-cache bash jq sudo git mosquitto mosquitto-clients openssl sed

COPY --from=udmi /root/udmi/bin /root/udmi/bin  
COPY --from=udmi /root/udmi/etc /root/udmi/etc  

COPY mosquitto_startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/mosquitto_startup.sh

CMD ["/usr/local/bin/mosquitto_startup.sh"]

