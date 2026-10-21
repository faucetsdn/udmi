#!/bin/bash -e

echo Starting FTP 21514 and open default 20,21
nc -nvlt -p 20 &
nc -nvlt -p 21 &
(while true; do echo -e "220 ProFTPD 1.3.5e Server (Debian) $(hostname)" | nc -l -w 1 21514; done) &

echo Starting SMTP 1256 and open default 25, 465, 587
nc -nvlt -p 25 &
nc -nvlt -p 465 &
nc -nvlt -p 587 &
(while true; do echo -e "220 $(hostname) ESMTP Postfix (Ubuntu)" | nc -l -w 1 1256; done) &

echo Starting IMAP 5361 and open default ports 143, 993
nc -nvlt -p 143 &
nc -nvlt -p 993 &
(while true; do echo -e "* OK [CAPABILITY IMAP4rev1 LITERAL+ SASL-IR LOGIN-REFERRALS ID ENABLE IDLE STARTTLS AUTH=PLAIN] Dovecot (Ubuntu) ready.\r\n" \
    | nc -l -w 1 5361; done) &

echo Starting POP3 23451 and open default 110, 995
nc -nvlt -p 110 &
nc -nvlt -p 995 &
(while true; do echo -ne "+OK POP3 Server ready\r\n" | nc -l -w 1 23451; done) &

echo starting TFTP UDP 69
(while true; do echo -ne "\0\x05\0\0\x07\0" | nc -u -l -w 1 69; done) &


mkdir /var/run/sshd
chmod 0755 /var/run/sshd
/usr/sbin/sshd

/venv/bin/python3 bacnet_server.py