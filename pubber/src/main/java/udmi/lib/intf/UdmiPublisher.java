package udmi.lib.intf;

import static java.util.Optional.ofNullable;

import udmi.lib.base.MqttDevice;
import udmi.schema.Config;
import udmi.schema.PubberConfiguration;

/**
 * Abstract interface for some kind of publishing stuff.
 */
public interface UdmiPublisher extends ManagerHost {

  static String getGatewayId(String targetId, PubberConfiguration configuration) {
    return ofNullable(configuration.gatewayId).orElse(
        targetId.equals(configuration.deviceId) ? null : configuration.deviceId);
  }

  MqttDevice getMqttDevice(String deviceId);

  void configPreprocess(String deviceId, Config config);

  void publisherConfigLog(String apply, Exception e, String deviceId);
}
