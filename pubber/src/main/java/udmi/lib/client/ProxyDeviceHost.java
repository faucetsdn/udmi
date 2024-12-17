package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;
import static udmi.lib.base.ManagerBase.updateStateHolder;
import static udmi.lib.base.MqttPublisher.DEFAULT_CONFIG_WAIT_SEC;

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

  UdmiPublisher getUdmiPublisher();

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
    while (getUdmiPublisher().isConnected() && !isActive().get()) {
      try {
        isActive().set(false);
        info("Activating proxy device " + getDeviceId());
        MqttDevice mqttDevice = getUdmiPublisher().getMqttDevice(getDeviceId());
        if (mqttDevice == null) {
          throw new RuntimeException("Publisher is not connected");
        }
        mqttDevice.registerHandler(MqttDevice.CONFIG_TOPIC, this::configHandler, Config.class);
        mqttDevice.connect(getDeviceId());
        getDeviceManager().activate();
        isActive().set(true);
      } catch (Exception e) {
        error(format("Could not connect proxy device %s: %s",
            getDeviceId(), friendlyStackTrace(e)));
        safeSleep(DEFAULT_CONFIG_WAIT_SEC * 1000);
      }
    }
  }

  /**
   * Configures the handler with the given configuration.
   *
   * @param config The configuration to be applied.
   */
  default void configHandler(Config config) {
    getUdmiPublisher().configPreprocess(getDeviceId(), config);
    getDeviceManager().updateConfig(config);
    getUdmiPublisher().publisherConfigLog("apply", null, getDeviceId());
  }

  void shutdown();

  void stop();

  @Override
  default void publish(Object message) {
    if (isActive().get()) {
      getUdmiPublisher().publish(getDeviceId(), message);
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
