package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static java.lang.String.format;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.ManagerBase;
import udmi.lib.ManagerHost;
import udmi.lib.MqttDevice;
import udmi.lib.client.DeviceManagerClient;
import udmi.lib.client.ProxyDeviceHostClient;
import udmi.lib.client.UdmiPublisherClient;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class ProxyDevice extends ManagerBase implements ProxyDeviceHostClient {

  private static final long STATE_INTERVAL_MS = 1000;
  final DeviceManager deviceManager;
  final Pubber pubberHost;
  private final AtomicBoolean active = new AtomicBoolean();

  /**
   * New instance.
   */
  public ProxyDevice(ManagerHost host, String id, PubberConfiguration pubberConfig) {
    super(host, makeProxyConfiguration(host, id, pubberConfig));
    // Simple shortcut to get access to some foundational mechanisms inside of Pubber.
    pubberHost = (Pubber) host;
    deviceManager = new DeviceManager(this, makeProxyConfiguration(host, id, pubberConfig));
    executor.scheduleAtFixedRate(this::publishDirtyState, STATE_INTERVAL_MS, STATE_INTERVAL_MS,
        TimeUnit.MILLISECONDS);
  }

  private static PubberConfiguration makeProxyConfiguration(ManagerHost host, String id,
      PubberConfiguration config) {
    PubberConfiguration proxyConfiguration = deepCopy(config);
    proxyConfiguration.deviceId = id;
    Metadata metadata = ((Pubber) host).getMetadata(id);
    proxyConfiguration.serialNo = catchToNull(() -> metadata.system.serial_no);
    return proxyConfiguration;
  }

  @Override
  public void activate() {
    try {
      active.set(false);
      info("Activating proxy device " + deviceId);
      MqttDevice mqttDevice = pubberHost.getMqttDevice(deviceId);
      mqttDevice.registerHandler(MqttDevice.CONFIG_TOPIC, this::configHandler, Config.class);
      mqttDevice.connect(deviceId);
      deviceManager.activate();
      active.set(true);
    } catch (Exception e) {
      error(format("Could not connect proxy device %s: %s", deviceId, friendlyStackTrace(e)));
    }
  }

  @Override
  public void configHandler(Config config) {
    pubberHost.configPreprocess(deviceId, config);
    deviceManager.updateConfig(config);
    pubberHost.publisherConfigLog("apply", null, deviceId);
  }

  @Override
  public void shutdown() {
    deviceManager.shutdown();
  }

  @Override
  public void stop() {
    deviceManager.stop();
  }

  private void publishDirtyState() {
    if (stateDirty.getAndSet(false)) {
      pubberHost.publish(deviceId, deviceState);
    }
  }

  @Override
  public void setMetadata(Metadata metadata) {
    deviceManager.setMetadata(metadata);
  }

  @Override
  public DeviceManagerClient getDeviceManager() {
    return deviceManager;
  }

  @Override
  public UdmiPublisherClient getUdmiPublisherHost() {
    return pubberHost;
  }

  @Override
  public ManagerHost getManagerHost() {
    return host;
  }

  @Override
  public AtomicBoolean isActive() {
    return active;
  }

}
