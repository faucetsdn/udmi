
# Proxy

Example implementation of a HAProxy at four endpoints (HOST1:443,HOST1:8883,HOST2:443,HOST2:8883), pulling configuration from GCS and publishing connection/disconnection over MQTT.

Connection MQTT payload

```json
{
	"version": "1.4.1",
	"timestamp": "2023-05-11T14: 59: 17.943000Z",
	"connection": {
		"action": "connect",
		"connack_response": "",
		"protocol": "mqtt",
		"hostname": "proxya.digitalbuilding.uk",
		"port": 443,
		"client_id": "projects/@MQTT_PROJECT_ID@/locations/us-central1/registries/registrar_test/devices/AHU-12"
	}
}
```

Disconnection MQTT payload
```json
{
	"version": "1.4.1",
	"timestamp": "2023-05-11T14: 59: 17.945000Z",
	"connection": {
		"action": "disconnect",
		"connack_response": "5",
		"protocol": "mqtt",
		"hostname": "proxya.digitalbuilding.uk",
		"port": 443,
		"client_id": "projects/@MQTT_PROJECT_ID@/locations/us-central1/registries/registrar_test/devices/AHU-12"
	}
```
(`connack_response`: 5 --> not authorised, 0 --> authorised)


# Setup

Replace all variables `@VAR_NAME@` in files.
`$ grep -rE '@[A-Za-z_]*@' ./`


## Setup Cloud
`terraform/setup.sh`

## Terraform 
```
terraform import google_project.mqttproxy-project @GCP_PROJECT_ID@
terraform import google_storage_bucket.tf-buca
terraform apply
```

## Build Images

### HAProxy

`haproxy/build.sh`

To upload config, modify `haproxy.cfg` and run `haproxy/update_config.sh`

The configuration file is pulled from GCS on container load

### Syslog

Update hardcoded MQTT properties in `server.py` and then build.

`syslog/build.sh`

## GKE

Update `syslog.yaml` and `haproxy-deployment.yaml` with the image digests from gcr.io (output from build.sh files)

```
kubectl apply -f <ALL YAML FILES>
```

## Setup SSL Proxies

```k8s/post.sh`