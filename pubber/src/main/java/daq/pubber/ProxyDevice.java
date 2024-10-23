package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static java.lang.String.format;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.base.MqttDevice;
import udmi.lib.client.DeviceManager;
import udmi.lib.client.ProxyDeviceHost;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class ProxyDevice extends PubberManager implements ProxyDeviceHost {

  private static final long STATE_INTERVAL_MS = 1000;
  final PubberDeviceManager deviceManager;
  final Pubber pubberHost;
  private final AtomicBoolean active = new AtomicBoolean();

  /**
   * New instance.
   */
  public ProxyDevice(ManagerHost host, String id, PubberConfiguration pubberConfig) {
    super(host, makeProxyConfiguration(host, id, pubberConfig));
    // Simple shortcut to get access to some foundational mechanisms inside of Pubber.
    pubberHost = (Pubber) host;
    deviceManager = new PubberDeviceManager(this, makeProxyConfiguration(host, id,
        pubberConfig));
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
  public void publish(String targetId, Object message) {
    pubberHost.publish(targetId, message);
  }

  @Override
  public DeviceManager getDeviceManager() {
    return deviceManager;
  }

  @Override
  public PubberUdmiPublisher getUdmiPublisher() {
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
