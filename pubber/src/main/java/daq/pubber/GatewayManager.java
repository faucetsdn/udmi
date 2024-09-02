package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;

import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import udmi.schema.Config;
import udmi.schema.Entry;
import udmi.schema.GatewayConfig;
import udmi.schema.GatewayState;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;
import udmi.schema.PubberConfiguration;

/**
 * Manager for UDMI gateway functionality.
 */
public class GatewayManager extends ManagerBase {

  private static final String EXTRA_PROXY_DEVICE = "XXX-1";
  private static final String EXTRA_PROXY_POINT = "xxx_conflagration";
  private Map<String, ProxyDevice> proxyDevices;
  private SiteModel siteModel;
  private Metadata metadata;
  private GatewayState gatewayState;

  public GatewayManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
  }

  private Map<String, ProxyDevice> createProxyDevices(List<String> proxyIds) {
    if (proxyIds == null) {
      return Map.of();
    }

    Map<String, ProxyDevice> devices = new HashMap<>();

    String firstId = proxyIds.stream().sorted().findFirst().orElse(null);
    String noProxyId = ifTrueGet(isTrue(options.noProxy), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device %s", noProxyId)));
    proxyIds.stream().filter(not(id -> id.equals(noProxyId)))
        .forEach(id -> devices.put(id, new ProxyDevice(host, id, config)));

    ifTrueThen(options.extraDevice, () -> devices.put(EXTRA_PROXY_DEVICE, makeExtraDevice()));

    return devices;
  }

  /**
   * Publish log message for target device.
   */
  public void publishLogMessage(Entry logEntry, String targetId) {
    ifNotNullThen(proxyDevices, p -> p.values().forEach(pd -> {
      if (pd.deviceId.equals(targetId)) {
        pd.deviceManager.publishLogMessage(logEntry, targetId);
      }
    }));
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
    proxyDevices = ifNotNullGet(metadata.gateway, g -> createProxyDevices(g.proxy_ids));
  }

  public void activate() {
    ifNotNullThen(proxyDevices, p -> p.values().forEach(ProxyDevice::activate));
  }

  ProxyDevice makeExtraDevice() {
    return new ProxyDevice(host, EXTRA_PROXY_DEVICE, config);
  }

  /**
   * Update gateway operation based off of a gateway configuration block. This happens in two
   * slightly different forms, one for the gateway proper (primarily indicating what devices
   * should be proxy targets), and the other for the proxy devices themselves.
   */
  public void updateConfig(GatewayConfig gateway) {
    if (gateway == null) {
      gatewayState = null;
      updateState();
      return;
    }
    ifNullThen(gatewayState, () -> gatewayState = new GatewayState());

    ifNotNullThen(proxyDevices,
        p -> ifTrueThen(p.containsKey(EXTRA_PROXY_DEVICE), this::configExtraDevice));

    if (gateway.proxy_ids == null || gateway.target != null) {
      try {
        String addr = catchToNull(() -> gateway.target.addr);
        String family = ofNullable(catchToNull(() -> gateway.target.family))
            .orElse(ProtocolFamily.VENDOR);
        validateGatewayFamily(family, addr);
        setGatewayStatus(GATEWAY_PROXY_TARGET, Level.DEBUG, "gateway target family " + family);
      } catch (Exception e) {
        setGatewayStatus(GATEWAY_PROXY_TARGET, Level.ERROR, e.getMessage());
      }
    }
    updateState();
  }

  private void setGatewayStatus(String category, Level level, String message) {
    // TODO: Implement a map or tree or something to properly handle different error sources.
    gatewayState.status = new Entry();
    gatewayState.status.category = category;
    gatewayState.status.level = level.value();
    gatewayState.status.message = message;
    gatewayState.status.timestamp = getNow();
  }

  private void updateState() {
    updateState(ofNullable((Object) gatewayState).orElse(GatewayState.class));
  }

  private void validateGatewayFamily(String family, String addr) {
    if (!ProtocolFamily.FAMILIES.contains(family)) {
      throw new IllegalArgumentException("Unrecognized address family " + family);
    }

    String expectedAddr = catchToNull(() -> metadata.localnet.families.get(family).addr);

    if (expectedAddr != null && !expectedAddr.equals(addr)) {
      throw new IllegalStateException(
          format("Family address was %s, expected %s", addr, expectedAddr));
    }
  }

  private void configExtraDevice() {
    Config config = new Config();
    config.pointset = new PointsetConfig();
    config.pointset.points = new HashMap<>();
    PointPointsetConfig pointPointsetConfig = new PointPointsetConfig();
    config.pointset.points.put(EXTRA_PROXY_POINT, pointPointsetConfig);
    proxyDevices.get(EXTRA_PROXY_DEVICE).configHandler(config);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    ifNotNullThen(proxyDevices, p -> p.values().forEach(ProxyDevice::shutdown));
  }

  @Override
  public void stop() {
    super.stop();
    ifNotNullThen(proxyDevices, p -> p.values().forEach(ProxyDevice::stop));
  }

  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    processMetadata();
  }

  private void processMetadata() {
    ifNotNullThen(proxyDevices, p -> p.values().forEach(proxy -> {
      Metadata metadata = ifNotNullGet(siteModel, s -> s.getMetadata(proxy.deviceId));
      metadata = ofNullable(metadata).orElse(new Metadata());
      proxy.setMetadata(metadata);
    }));
  }
}
