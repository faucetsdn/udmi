package daq.pubber;

public class GatewayError {
  public String error_type;
  public String description;
  public String device_id;
  public MqttMessageInfo mqtt_message_info;

  public static class MqttMessageInfo {
   public String message_type;
   public String topic;
   public String packet_id;
  }
}
