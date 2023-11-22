package daq.pubber;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import udmi.schema.Config;
import udmi.schema.GatewayConfig;
import udmi.schema.GatewayModel;
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

  public GatewayManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
  }

  private Map<String, ProxyDevice> createProxyDevices(List<String> proxyIds) {
    if (proxyIds == null) {
      return ImmutableMap.of();
    }

    String firstId = proxyIds.stream().sorted().findFirst().orElseThrow();
    String noProxyId = ifTrueGet(isTrue(options.noProxy), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device " + noProxyId)));
    Map<String, ProxyDevice> devices = proxyIds.stream().filter(not(id -> id.equals(noProxyId)))
        .collect(toMap(k -> k, v -> new ProxyDevice(host, v)));

    ifTrueThen(options.extraDevice, () -> devices.put(EXTRA_PROXY_DEVICE, makeExtraDevice()));

    return devices;
  }

  public void setMetadata(GatewayModel gateway) {
    proxyDevices = ifNotNullGet(gateway, g -> createProxyDevices(g.proxy_ids));
  }

  public void activate() {
    ifNotNullThen(proxyDevices, p -> p.values().forEach(ProxyDevice::activate));
  }

  ProxyDevice makeExtraDevice() {
    return new ProxyDevice(host, EXTRA_PROXY_DEVICE);
  }

  public void updateConfig(GatewayConfig gateway) {
    ifNotNullThen(proxyDevices,
        p -> ifTrueThen(p.containsKey(EXTRA_PROXY_DEVICE), this::configExtraDevice));
  }

  private void configExtraDevice() {
    Config config = new Config();
    config.pointset = new PointsetConfig();
    config.pointset.points = new HashMap<>();
    PointPointsetConfig pointPointsetConfig = new PointPointsetConfig();
    config.pointset.points.put(EXTRA_PROXY_POINT, pointPointsetConfig);
    proxyDevices.get(EXTRA_PROXY_DEVICE).configHandler(config);
  }
}
