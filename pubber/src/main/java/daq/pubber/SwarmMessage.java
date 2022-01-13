package daq.pubber;

import com.google.daq.mqtt.util.CloudIotConfig;
import udmi.schema.Metadata;

public class SwarmMessage {
  public String device_id;
  public String key_base64;
  public Metadata device_metadata;
  public CloudIotConfig cloud_iot_config;
}
