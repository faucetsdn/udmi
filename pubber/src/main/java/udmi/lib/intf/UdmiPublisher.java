package udmi.lib.intf;

import udmi.lib.base.MqttDevice;
import udmi.schema.Config;

public interface UdmiPublisher extends ManagerHost {

  MqttDevice getMqttDevice(String deviceId);

  void configPreprocess(String deviceId, Config config);

  void publisherConfigLog(String apply, Object o, String deviceId);
}
