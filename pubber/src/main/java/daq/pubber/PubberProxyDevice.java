package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.client.DeviceManager;
import udmi.lib.client.ProxyDeviceHost;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class PubberProxyDevice extends PubberManager implements ProxyDeviceHost {

  private static final long STATE_INTERVAL_MS = 1000;
  final PubberDeviceManager deviceManager;
  final Pubber pubberHost;
  private final AtomicBoolean active = new AtomicBoolean();

  /**
   * New instance.
   */
  public PubberProxyDevice(ManagerHost host, String id, PubberConfiguration pubberConfig) {
    super(host, makeProxyConfiguration(host, id, pubberConfig));
    // Simple shortcut to get access to some foundational mechanisms inside of Pubber.
    pubberHost = (Pubber) host;
    deviceManager = new PubberDeviceManager(this, makeProxyConfiguration(host, id,
        pubberConfig));
    deviceManager.setSiteModel(pubberHost.getSiteModel());
    executor.scheduleAtFixedRate(this::publishDirtyState, STATE_INTERVAL_MS, STATE_INTERVAL_MS,
        TimeUnit.MILLISECONDS);
  }

  private static PubberConfiguration makeProxyConfiguration(ManagerHost host, String id,
      PubberConfiguration config) {
    PubberConfiguration proxyConfiguration = deepCopy(config);
    proxyConfiguration.deviceId = id;
    Metadata metadata = ((Pubber) host).getSiteModel().getMetadata(id);
    proxyConfiguration.serialNo = catchToNull(() -> metadata.system.serial_no);
    return proxyConfiguration;
  }

  @Override
  public void shutdown() {
    deviceManager.shutdown();
    isActive().set(false);
  }

  @Override
  public void stop() {
    deviceManager.stop();
    isActive().set(false);
  }

  private void publishDirtyState() {
    if (stateDirty.getAndSet(false)) {
      publish(deviceId, deviceState);
    }
  }

  @Override
  public void publish(String targetId, Object message) {
    publish(message);
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
