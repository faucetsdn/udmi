package daq.pubber.impl.manager;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.util.Optional.ofNullable;

import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberManager;
import daq.pubber.impl.host.PubberProxyHost;
import java.util.Map;
import udmi.lib.client.host.ProxyHost;
import udmi.lib.client.manager.GatewayManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.GatewayConfig;
import udmi.schema.GatewayState;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Manager for UDMI gateway functionality.
 */
public class PubberGatewayManager extends PubberManager implements GatewayManager {

  private Map<String, ProxyHost> proxyDevices;
  private GatewayState gatewayState;
  private Metadata metadata;

  public PubberGatewayManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  @Override
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
    GatewayManager.super.setMetadata(metadata);
  }

  @Override
  public GatewayState getGatewayState() {
    return gatewayState;
  }

  @Override
  public void setGatewayState(GatewayState gatewayState) {
    this.gatewayState = gatewayState;
  }

  @Override
  public Map<String, ProxyHost> getProxyDevices() {
    return proxyDevices;
  }

  @Override
  public void setProxyDevices(Map<String, ProxyHost> proxyDevices) {
    this.proxyDevices = proxyDevices;
  }

  @Override
  public void shutdown() {
    super.shutdown();
    GatewayManager.super.shutdown();
  }

  @Override
  public void stop() {
    super.stop();
    GatewayManager.super.stop();
  }

  @Override
  public ProxyHost createProxyDevice(ManagerHost host, String id) {
    return new PubberProxyHost(host, id, config);
  }

  @Override
  public ProxyHost makeExtraDevice() {
    return new PubberProxyHost(getHost(), EXTRA_PROXY_DEVICE, config);
  }

  @Override
  public void syncDevices(GatewayConfig gatewayConfig) {
  }

  /**
   * Set site model.
   */
  public void setSiteModel(SiteModel siteModel) {
    ifNotNullThen(proxyDevices, p -> p.values().forEach(proxy -> {
      Metadata localMetadata = ifNotNullGet(siteModel, s -> s.getMetadata(proxy.getDeviceId()));
      localMetadata = ofNullable(localMetadata).orElse(new Metadata());
      proxy.setMetadata(localMetadata);
    }));
  }
}
