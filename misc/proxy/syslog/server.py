import socketserver
import datetime
import ssl
import json
import jwt
import paho.mqtt.client as mqtt
import re
import sys
from dataclasses import dataclass


#<134>Feb 26 18:09:42 haproxy[18]:open 26/Feb/2023:18:09:42.777 mqtt4 broker_gcp/gcp projects/@MQTT_PROJECT_ID@/locations/us-central1/registries/registrar_test/devices/AHU-12

def interesting_event(message):
  """ Check if recieved message has been passed along to a backend """
  return (message[-2:] != '-1')


class Timestamp():
  def __init__(self, haproxy_timestamp):
    self.timestamp_obj = self._parse_timestamp(haproxy_timestamp)

  def _parse_timestamp(self, haproxy_timestamp):
    return datetime.datetime.strptime(haproxy_timestamp, '%d/%b/%Y:%H:%M:%S.%f')
  
  def __reprt__(self):
    return self.timestamp_obj

  def __str__(self):
    return self.timestamp_obj.isoformat() + 'Z'

@dataclass
class Endpoint():
  hostname: str
  port: int


class ProxyEvent():
  
  frontend_map = {
    'mqtt1': Endpoint('@HOSTNAME_1@', 443),
    'mqtt2': Endpoint('@HOSTNAME_1@', 8883),
    'mqtt3': Endpoint('@HOSTNAME_1@', 443),
    'mqtt4': Endpoint('@HOSTNAME_1@', 8883)
  }

  def get_endpoint(self, key):
    if key in self.frontend_map:
      return self.frontend_map[key]
    
    if key[:-1] in self.frontend_map:
      return self.frontend_map[key[:-1]]

    raise Exception("unknown frontend")

  def __init__(self, message):
    self.message = message

    # Two colons for time and one for process
    log_message = message.split(':', 3)[3].strip()
    log_bits = log_message.split(' ')
    self.connect = True if log_bits[0] == 'open' else False
    self.action = 'connect' if self.connect else 'disconnect'
    self.timestamp_raw = log_bits[1]
    self.endpoint = self.get_endpoint(log_bits[2])
    self.backend = log_bits[3]
    self.client_id = log_bits[4]
    self.connack_response = log_bits[5] if not self.connect else ''

    self.timestamp = Timestamp(self.timestamp_raw)

  def get_json(self):
    return {
        'version': '1.4.1',
        # 'timestamp': datetime.datetime.utcnow().isoformat() + 'Z',
        'timestamp': str(self.timestamp), 
        'connection': {
          'action': self.action,
          'connack_response': self.connack_response,
          'protocol': 'mqtt',
          'hostname': self.endpoint.hostname, 
          'port': self.endpoint.port, 
          'client_id': self.client_id 
        }
      }

  def parse_haproxy_timestamp(self, timestamp):
    pass    

  def __str__(self):
    return str(self.message)


""" UDP Server Message Handler """
class SyslogHandler(socketserver.BaseRequestHandler):

  def handle(self):
    data = bytes.decode(self.request[0].strip())
    print(data)

    if interesting_event(data):
      rep = ProxyEvent(data)
      mqtt_client.publish('/devices/AHU-1/events/system', json.dumps(rep.get_json()))
      print(rep.get_json())


""" MQTT Client """
class MqttClient():
  connected = False
  disconect_count = 0
  project_id = '@MQTT_PROJECT_ID@'
  private_key_file ='rsa_private.pem'
  algorithm = 'RS256'
  cloud_region = 'us-central1'
  registry_id = 'registrar_test'
  device_id = 'AHU-1'
  ca_certs = 'roots.pem'
  mqtt_bridge_hostname = 'mqtt.googleapis.com'
  mqtt_bridge_port = 8883
  jwt_exp_mins = 20

  def create_jwt(self):
    self.jwt_iat = datetime.datetime.now(tz=datetime.timezone.utc)
    token = {
      "iat": self.jwt_iat,
      "exp": datetime.datetime.now(tz=datetime.timezone.utc) + datetime.timedelta(minutes=self.jwt_exp_mins),
      "aud": self.project_id,
    }
    with open(self.private_key_file, "r") as f:
      private_key = f.read()
    return jwt.encode(token, private_key, algorithm=self.algorithm)

  def error_str(self, rc):
    return "{}: {}".format(rc, mqtt.error_string(rc))

  def on_connect(self, client, ud, flag, rc):
    if rc == 0:
      self.connected = True
    print(datetime.datetime.now(),"on_connect", mqtt.connack_string(rc))

  def on_disconnect(self, client, userdata, rc):
    self.connected = False
    print(datetime.datetime.now(),"on_disconnect", self.error_str(rc))

  def on_publish(self, client, userdata, mid):
    print(f'published message')
    
  def get_client(self):
    client_id = "projects/{}/locations/{}/registries/{}/devices/{}".format(
      self.project_id, self.cloud_region, self.registry_id, self.device_id
    )

    client = mqtt.Client(client_id=client_id)
    client.username_pw_set(
      username="unused", password=self.create_jwt()
    )

    client.tls_set(ca_certs=self.ca_certs, tls_version=ssl.PROTOCOL_TLSv1_2)
    client.on_connect = self.on_connect
    client.on_disconnect = self.on_disconnect
    client.on_publish = self.on_publish
    client.connect(self.mqtt_bridge_hostname, self.mqtt_bridge_port, keepalive=5)
    
    return client

  def start_client(self):
    self.client = self.get_client()
    self.client.loop_start()

  def loop(self):
    if self.disconect_count > 5:
      raise(Exception('not connected'))

    if not self.connected:
      self.disconect_count +=1 
      self.client.loop_stop()
      self.start_client()

    seconds_since_issue = (datetime.datetime.now(tz=datetime.timezone.utc) - self.jwt_iat).seconds
    if seconds_since_issue > 60 * self.jwt_exp_mins - 3:
      print(datetime.datetime.now(),"Refreshing JWT after  {}s".format(seconds_since_issue))
      self.client.disconnect()
      self.client.loop_stop()
      self.start_client()

  def publish(self, mqtt_topic, message):
    return self.client.publish(mqtt_topic, message)

mqtt_client = MqttClient()
mqtt_client.start_client()

print('starting server on 0.0.0.0:514')
server = socketserver.UDPServer(('0.0.0.0', 514), SyslogHandler)
server.timeout = 5

while(True):
  server.handle_request()
  mqtt_client.loop()

