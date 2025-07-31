from abc import ABC, abstractmethod
import argparse
from dataclasses import dataclass, field, replace
import datetime
import json
import logging
import queue
import random
import ssl
import sys
import threading
import time
from typing import Any
from typing import Callable
import jwt
import paho.mqtt.client
import paho.mqtt.enums
import udmi.publishers.publisher
import hashlib


class MQTT(udmi.publishers.publisher.Publisher):

  def __init__(
    self,
    *,
    device_id: str,
    registry_id: str,
    region: str,
    project_id,
    hostname: str,
    port: int,
    key_file: str,
    public_key_file: str,
    algorithm: str,
    topic_prefix: str,
    jwt_minutes=60,
    ca_file: str = None,
    cert_file: str = None,
    autentication_mechanism: str = "jwt_gcp",
  ):
    """Initializes the MQTT client.

    Args:
      device_id: the device id
      registry_id: the registry id
      region: the region
      project_id: the project id
      hostname: the hostname
      port: the port
      key_file: the key file
      public_key_file: path to public key
      algorithm: the algorithm
      jwt_minutes: the jwt minutes
    """

    self.public_key_printed_last_time = 0
    self.public_key_print_interval_seconds: int = 3600 #s
    self.public_key_file: str | None = None
    self.public_key: str | None = None

    self.logger = logging.getLogger("mqtt_client")
    self.logger.setLevel(logging.DEBUG)
    self.config_callback = None

    self.device_id = device_id
    self.registry_id = registry_id
    self.topic_prefix = topic_prefix
    self.mqtt_host = hostname
    self.mqtt_port = port

    self.private_key_file = key_file
    self.public_key_file = public_key_file
    self.jwt_exp_mins = jwt_minutes
    self.project_id = project_id
    self.algorithm = algorithm

    self.ca_file = ca_file
    self.cert_file = cert_file
    self.autentication_mechanism = autentication_mechanism
    
    match autentication_mechanism:
      case "jwt_gcp":
        self.client_id = f"projects/{project_id}/locations/{region}/registries/{registry_id}/devices/{device_id}"
        self.username = "unused"
      case "udmi_local":
        self.username = f"/r/{registry_id}/d/{device_id}"
        self.client_id = self.username
        pkcs_file = f"{key_file.rpartition('.')[0]}.pkcs8"
        with open(pkcs_file, 'rb') as f:
          key_bytes = f.read()
          hash = hashlib.sha256(key_bytes).hexdigest()
          self.password = hash[:8]
          print(self.password)
      case _:
        raise RuntimeError("unknown authentication mechanism")
      
    

    self.make_client()

  def _create_jwt(self):
    self.logger.info("creating jwt")
    jwt_iat = datetime.datetime.now(tz=datetime.timezone.utc)
    self.jwt_expiry = jwt_iat + datetime.timedelta(minutes=self.jwt_exp_mins)
    token = {
      "iat": jwt_iat,
      "exp": self.jwt_expiry,
      "aud": self.project_id,
    }

    with open(self.private_key_file, "r") as f:
      private_key = f.read()

    return jwt.encode(token, private_key, algorithm=self.algorithm)

  ##############################################################################
  ##############################################################################
  ## Callbacks
  ##############################################################################
  def on_connect(
    self,
    client: paho.mqtt.client.Client,
    userdata: Any,
    flags: paho.mqtt.client.ConnectFlags,
    reason_code: paho.mqtt.client.ReasonCode,
    properties: paho.mqtt.client.Properties,
  ):
    """Callback for when a device connects.

    Handles internal logic and to subscribe to topics.

    Args:
      client: the client instance for this callback
      userdata: the private user data as set in paho.mqtt.client.Client() or
      user_data_set()
      connect_flags: flags for this connection
      reason_code: connection reason code received from the broken. In MQTT v5.0
      it is the reason code defined by the standard. In MQTT v3, we convert
      return code to a reason code, see convert_connack_rc_to_reason_code().
      ReasonCode may be compared to integer.
      properties: the MQTT v5.0 properties received from the broker. For MQTT
      v3.1 and v3.1.1 properties is not provided and an empty Properties
      object is always used.
    """
    del client, userdata, flags, properties  # Unused, part of callback API.

    self.logger.info(f"on_connect {reason_code}")
    if reason_code == 0:
      self.client.subscribe(f"{self.topic_prefix}/config", qos=1)
    else:
      self.print_public_key()

  def on_disconnect(
    self,
    client: paho.mqtt.client.Client,
    userdata: Any,
    disconnect_flags: paho.mqtt.client.DisconnectFlags,
    reason_code: paho.mqtt.client.ReasonCode,
    properties: paho.mqtt.client.Properties,
  ):
    """Callback for when a device disconnects.

    Args:
      client:the client instance for this callback

    userdata: the private user data as set in Client() or user_data_set()
    disconnect_flags: the flags for this disconnection.
    reason_code: the disconnection reason code possibly received from the broker
      (see disconnect_flags). In MQTT v5.0 it iss the reason code defined by the
      standard. In MQTT v3 it is never received from the broker, we convert an
      MQTTErrorCode, see convert_disconnect_error_code_to_reason_code().
      ReasonCode may be compared to integer.
    properties: the MQTT v5.0 properties received from the broker. For MQTT v3.1
      and v3.1.1 properties is not provided and an empty Properties object is
      always used.
    """
    del (
      client,
      userdata,
      disconnect_flags,
      properties,
    )  # Unused, part of callback API.

    if reason_code == 0:
      self.logger.info(f"on_disconnect {reason_code}")
    else:
      self.logger.error(f"on_disconnect {reason_code}")


  def on_pre_connect(self, client, userdata):
    """Paho preconnect callback.

    This is a blocking call made just before a PAHO makes a connection is made.

    Used to set JWT for the connection

    Args:
      client: the client instance for this callback
      userdata: the private user data as set in Client() or user_data_set()
    """
    del userdata  # Unused, part of callback API.
    self.logger.info("client ID is %s", self.client_id)
    if self.autentication_mechanism == "jwt_gcp":
      client.username_pw_set(username="unused", password=self._create_jwt())
    else:
      client.username_pw_set(username=self.username, password=self.password)

  def on_message(
    self,
    client: paho.mqtt.client.Client,
    userdata: Any,
    message: paho.mqtt.client.MQTTMessage,
  ):
    """Callback for when a message is received.

    Args:
      client: the client instance for this callback
      userdata: the private user data as set in Client() or user_data_set()
      message :the received message. This is a class with members topic,
      payload, qos, retain.
    """
    del client, userdata  # Unused, part of callback API.

    self.logger.info("received config")

    if self.config_callback:
      self.logger.debug("dispatched config to %s", self.config_callback)
      self.config_callback(message.payload.decode("utf-8"))
    else:
      self.logger.warning("no config callback")

    # self.logger.debug(message.payload.decode("utf-8"))

  def make_client(self):
    """Creates MQTT client."""

    client = paho.mqtt.client.Client(
      client_id=self.client_id,
      clean_session=True,
      callback_api_version=paho.mqtt.enums.CallbackAPIVersion.VERSION2,
    )

    if self.autentication_mechanism == "jwt_gcp":
      client.tls_set(self.ca_file, tls_version=ssl.PROTOCOL_TLSv1_2)
    elif self.autentication_mechanism == "udmi_local":
      client.tls_set(
        self.ca_file,
        tls_version=ssl.PROTOCOL_TLSv1_2,
        keyfile=self.private_key_file,
        certfile=self.cert_file,
      )
      # I don't know why this doesn't like the UDMIS certs
      # ssl.SSLError: [SSL] PEM lib (_ssl.c:3874)
      # TODO: Investigate SSL errors with local UDMIS
      client.tls_insecure_set(True)
    else:
      raise RuntimeError("unknown authentication mechanism")

    client.on_connect = self.on_connect
    client.on_disconnect = self.on_disconnect
    client.on_message = self.on_message
    client.on_pre_connect = self.on_pre_connect

    self.client = client

  def start_client(self):
    """Starts the MQTT client."""
    self.client.connect(self.mqtt_host, self.mqtt_port, keepalive=60)
    self.client.loop_start()

  def set_config_callback(self, callback: Callable[[str], None]):
    self.config_callback = callback

  def publish_message(self, topic, message):
    logging.debug("publishing to: %s", topic)
    return self.client.publish(topic, message)

  def print_public_key(self) -> None:
    if (
        time.monotonic() - self.public_key_printed_last_time
        > self.public_key_print_interval_seconds
    ):
      
      if not self.public_key:
        with open(self.public_key_file, "r") as f:
          self.public_key = f.read()
      logging.warning(
        "%s/%s not authenticated. Public key: %s",
        self.registry_id,
        self.device_id, 
        self.public_key.encode("utf-8")
      )
      self.public_key_printed_last_time = time.monotonic()
