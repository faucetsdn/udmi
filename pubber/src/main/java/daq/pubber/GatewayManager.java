package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;

import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import udmi.schema.Common.ProtocolFamily;
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
    if (!proxyIds.isEmpty()) {
      String firstId = proxyIds.stream().sorted().findFirst().orElseThrow();
      String noProxyId = ifTrueGet(isTrue(options.noProxy), () -> firstId);
      ifNotNullThen(noProxyId, id -> warn(format("Not proxying device %s", noProxyId)));
      proxyIds.forEach(id -> {
        if (!id.equals(noProxyId)) {
          devices.put(id, new ProxyDevice(host, id));
        }
      });
    }

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
    return new ProxyDevice(host, EXTRA_PROXY_DEVICE);
  }

  /**
   * Update gateway operation based off of a gateway configuration block.
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
        ProtocolFamily family = validateGatewayFamily(catchToNull(() -> gateway.target.family));
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
  }

  private void updateState() {
    updateState(ofNullable((Object) gatewayState).orElse(GatewayState.class));
  }

  private ProtocolFamily validateGatewayFamily(ProtocolFamily family) {
    if (family == null) {
      return null;
    }
    debug("Validating gateway family " + family);
    Objects.requireNonNull(catchToNull(() -> metadata.localnet.families.get(family).addr),
        format("Address family %s addr is null or undefined", family));
    return family;
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
