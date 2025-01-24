package daq.pubber.impl.host;

import daq.pubber.impl.PubberManager;
import daq.pubber.impl.manager.PubberDeviceManager;
import java.util.concurrent.atomic.AtomicBoolean;
import udmi.lib.client.host.ProxyHost;
import udmi.lib.client.host.PublisherHost;
import udmi.lib.client.manager.DeviceManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class PubberProxyHost extends PubberManager implements ProxyHost {

  private final AtomicBoolean active = new AtomicBoolean();
  private final PubberDeviceManager deviceManager;
  private final PubberPublisherHost publisherHost;

  /**
   * New instance.
   */
  public PubberProxyHost(ManagerHost host, String id, PubberConfiguration pubberConfig) {
    super(host, makeProxyConfiguration(host, id, pubberConfig));
    publisherHost = (PubberPublisherHost) host;
    deviceManager = new PubberDeviceManager(this, makeProxyConfiguration(host, id, pubberConfig));
    deviceManager.setSiteModel(publisherHost.getSiteModel());
    schedulePeriodic(STATE_INTERVAL_SEC, this::publishDirtyState);
  }

  @Override
  public DeviceManager getDeviceManager() {
    return deviceManager;
  }

  @Override
  public PublisherHost getPublisherHost() {
    return publisherHost;
  }

  @Override
  public ManagerHost getManagerHost() {
    return host;
  }

  @Override
  public AtomicBoolean isActive() {
    return active;
  }

  @Override
  public void shutdown() {
    ProxyHost.super.shutdown();
  }

  @Override
  public void stop() {
    ProxyHost.super.stop();
  }
}
