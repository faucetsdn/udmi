package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static java.lang.String.format;
import static udmi.lib.base.ManagerBase.updateStateHolder;

import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.base.MqttDevice;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.ManagerLog;
import udmi.lib.intf.UdmiPublisher;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.State;

/**
 * Proxy Device host provider.
 */
public interface ProxyDeviceHost extends ManagerHost, ManagerLog {

  DeviceManager getDeviceManager();

  UdmiPublisher getUdmiPublisherHost();

  ManagerHost getManagerHost();

  AtomicBoolean isActive();

  String getDeviceId();

  /**
   * Activates the proxy device by setting its active status, registering a handler for
   * configuration updates, connecting to MQTT, activating the device manager,
   * and finally updating the active status.
   * Logs information or errors based on the success or failure of these operations.
   */
  default void activate() {
    try {
      isActive().set(false);
      MqttDevice mqttDevice = getUdmiPublisherHost().getMqttDevice(getDeviceId());
      mqttDevice.registerHandler(MqttDevice.CONFIG_TOPIC, this::configHandler, Config.class);
      mqttDevice.connect();
      getDeviceManager().activate();
      isActive().set(true);
      info("Activated proxy device " + getDeviceId());
    } catch (Exception e) {
      error(format("Could not connect proxy device %s: %s", getDeviceId(), friendlyStackTrace(e)));
    }
  }

  /**
   * Configures the handler with the given configuration.
   *
   * @param config The configuration to be applied.
   */
  default void configHandler(Config config) {
    getUdmiPublisherHost().configPreprocess(getDeviceId(), config);
    getDeviceManager().updateConfig(config);
    getUdmiPublisherHost().publisherConfigLog("apply", null, getDeviceId());
  }

  void shutdown();

  void stop();

  @Override
  default void publish(Object message) {
    if (isActive().get()) {
      getUdmiPublisherHost().publish(getDeviceId(), message);
    }
  }

  default void update(Object update) {
    updateStateHolder(getDeviceState(), update);
    getStateDirty().set(true);
  }


  @Override
  default FamilyProvider getLocalnetProvider(String family) {
    return getManagerHost().getLocalnetProvider(family);
  }

  default void setMetadata(Metadata metadata) {
    getDeviceManager().setMetadata(metadata);
  }

  void error(String message);


  AtomicBoolean getStateDirty();

  State getDeviceState();

}
