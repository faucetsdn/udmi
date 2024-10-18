package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static daq.pubber.ManagerBase.updateStateHolder;
import static java.lang.String.format;

import daq.pubber.FamilyProvider;
import daq.pubber.ManagerHost;
import daq.pubber.ManagerLog;
import daq.pubber.MqttDevice;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.State;

/**
 * Proxy Device host provider.
 */
public interface ProxyDeviceHostProvider extends ManagerHost, ManagerLog {

  DeviceManagerProvider getDeviceManager();

  PubberHostProvider getPubberHost();

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
      MqttDevice mqttDevice = getPubberHost().getMqttDevice(getDeviceId());
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
    getPubberHost().configPreprocess(getDeviceId(), config);
    getDeviceManager().updateConfig(config);
    getPubberHost().publisherConfigLog("apply", null, getDeviceId());
  }

  void shutdown();

  void stop();

  @Override
  default void publish(Object message) {
    if (isActive().get()) {
      getPubberHost().publish(getDeviceId(), message);
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
