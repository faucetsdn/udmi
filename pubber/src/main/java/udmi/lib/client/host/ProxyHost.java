package udmi.lib.client.host;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;
import static udmi.lib.base.ManagerBase.updateStateHolder;
import static udmi.lib.base.MqttPublisher.DEFAULT_CONFIG_WAIT_SEC;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.base.MqttDevice;
import udmi.lib.client.manager.DeviceManager;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.ManagerLog;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.State;

/**
 * Proxy Device host provider.
 */
public interface ProxyHost extends ManagerHost, ManagerLog {

  int STATE_INTERVAL_SEC = 1;

  DeviceManager getDeviceManager();

  PublisherHost getPublisherHost();

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
    while (getPublisherHost().isConnected() && !isActive().get()) {
      try {
        isActive().set(false);
        info(format("Activating proxy device %s", getDeviceId()));
        MqttDevice mqttDevice = getPublisherHost().getMqttDevice(getDeviceId());
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
    getPublisherHost().configPreprocess(getDeviceId(), config);
    getDeviceManager().updateConfig(config);
    getPublisherHost().publisherConfigLog("apply", null, getDeviceId());
  }

  default void shutdown() {
    getDeviceManager().shutdown();
    isActive().set(false);
  }

  default void stop() {
    getDeviceManager().stop();
    isActive().set(false);
  }

  /**
   * Publish dirty state.
   */
  default void publishDirtyState() {
    if (getStateDirty().getAndSet(false)) {
      publish(getDeviceId(), getDeviceState());
    }
  }

  @Override
  default void publish(String targetId, Object message) {
    publish(message);
  }

  @Override
  default void publish(Object message) {
    if (isActive().get()) {
      getPublisherHost().publish(getDeviceId(), message);
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

  @Override
  default Date getStartTime() {
    return getDeviceManager().getSystemManager().getStartTime();
  }
}
