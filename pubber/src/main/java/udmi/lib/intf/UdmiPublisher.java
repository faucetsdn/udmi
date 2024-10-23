package udmi.lib.intf;

import udmi.lib.base.MqttDevice;
import udmi.schema.Config;

/**
 * Abstract interface for some kind of publishing stuff.
 */
public interface UdmiPublisher extends ManagerHost {

  MqttDevice getMqttDevice(String deviceId);

  void configPreprocess(String deviceId, Config config);

  void publisherConfigLog(String phase, Exception e, String deviceId);
}
