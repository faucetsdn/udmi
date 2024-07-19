[**UDMI**](../../) / [**Docs**](../) / [**UDMIS**](.) / [UDMIS Output](#)

Commands to test if the UDMIS container is running properly:
```
docker logs udmis 2>&1 | fgrep udmis
fgrep pod_ready var/tmp/udmis.log
ls -l var/tmp/pod_ready.txt
```

Sample output:
```
export TARGET_PROJECT=//mqtt/udmis
bin/start_etcd: line 9: udmis/bin/etcdctl: No such file or directory
Starting mosquitto on server udmis
Generating CA with altname udmis
Generating CERT with altname udmis
Starting udmis proper...
bin/container: line 14: cd: /root/udmi/udmis: No such file or directory
Waiting for udmis startup 29...
Waiting for udmis startup 28...
Waiting for udmis startup 27...
Waiting for udmis startup 26...
Waiting for udmis startup 25...
::::::::: tail /tmp/udmis.log
udmis running in the background, pid 198 log in /tmp/udmis.log
2024-07-19T04:18:40Z xxxxxxxx N: UdmiServicePod Finished activation of container components, created /tmp/pod_ready.txt
-rw-r--r-- 1 root root 0 Jul 18 21:18 var/tmp/pod_ready.txt
```
