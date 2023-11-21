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

public class GatewayManager extends ManagerBase {

  private final Pubber pubberHost;
  private Map<String, ProxyDevice> proxyDevices;
  private static final String EXTRA_PROXY_DEVICE = "XXX-1";
  private static final String EXTRA_PROXY_POINT = "xxx_conflagration";

  public GatewayManager(Pubber host, PubberConfiguration configuration) {
    super(host, configuration);
    pubberHost = host;
  }

  private Map<String, ProxyDevice> createProxyDevices(List<String> proxyIds) {
    if (proxyIds == null) {
      return ImmutableMap.of();
    }

    String firstId = proxyIds.stream().sorted().findFirst().orElseThrow();
    String noProxyId = ifTrueGet(isTrue(options.noProxy), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device " + noProxyId)));
    Map<String, ProxyDevice> devices = proxyIds.stream().filter(not(id -> id.equals(noProxyId)))
        .collect(toMap(k -> k, v -> new ProxyDevice(pubberHost, v)));

    ifTrueThen(options.extraDevice, () -> devices.put(EXTRA_PROXY_DEVICE, makeExtraDevice()));

    return devices;
  }

  public void setMetadata (GatewayModel gateway){
    proxyDevices = ifNotNullGet(gateway, g -> createProxyDevices(g.proxy_ids));
  }

  ProxyDevice makeExtraDevice() {
    ProxyDevice proxyDevice = new ProxyDevice(this, EXTRA_PROXY_DEVICE);
    Config config = new Config();
    config.pointset = new PointsetConfig();
    config.pointset.points = new HashMap<>();
    PointPointsetConfig pointPointsetConfig = new PointPointsetConfig();
    config.pointset.points.put(EXTRA_PROXY_POINT, pointPointsetConfig);
    proxyDevice.configHandler(config);
    return proxyDevice;
  }

  public void updateConfig(GatewayConfig gateway) {
    ifTrueThen(proxyDevices.containsKey(EXTRA_PROXY_DEVICE), this::configExtraDevice);
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
