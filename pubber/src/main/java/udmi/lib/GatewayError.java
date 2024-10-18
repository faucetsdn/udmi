package udmi.lib;

/**
 * Handler class for a received error when binding a gateway.
 */
@SuppressWarnings("MemberName")
public class GatewayError {

  public String error_type;
  public String description;
  public String device_id;
  public MqttMessageInfo mqtt_message_info;

  /**
   * Encapsulating information about the message itself.
   */
  public static class MqttMessageInfo {

    public String message_type;
    public String topic;
    public String packet_id;
  }
}
