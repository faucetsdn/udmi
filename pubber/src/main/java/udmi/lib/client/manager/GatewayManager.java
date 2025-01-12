package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import udmi.lib.ProtocolFamily;
import udmi.lib.client.host.ProxyHost;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.Config;
import udmi.schema.Entry;
import udmi.schema.GatewayConfig;
import udmi.schema.GatewayState;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;

/**
 * Gateway client.
 */
public interface GatewayManager extends SubBlockManager {

  String EXTRA_PROXY_DEVICE = "XXX-1";
  String EXTRA_PROXY_POINT = "xxx_conflagration";

  Metadata getMetadata();

  default void setMetadata(Metadata metadata) {
    setProxyDevices(ifNotNullGet(metadata.gateway, g -> createProxyDevices(g.proxy_ids)));
  }

  GatewayState getGatewayState();

  Map<String, ProxyHost> getProxyDevices();

  void setProxyDevices(Map<String, ProxyHost> proxyDevices);

  /**
   * Creates a map of proxy devices.
   *
   * @param proxyIds A list of device IDs to create proxies for.
   * @return A map where each key-value pair represents a device ID and its corresponding proxy
   * @throws NoSuchElementException if no first element exists in the stream
   */
  default Map<String, ProxyHost> createProxyDevices(List<String> proxyIds) {
    List<String> deviceIds = ofNullable(proxyIds).orElseGet(ArrayList::new);
    String firstId = deviceIds.stream().sorted().findFirst().orElse(null);
    String noProxyId = ifTrueGet(isNoProxy(), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device %s", id)));
    List<String> filteredList = deviceIds.stream().filter(not(id -> id.equals(noProxyId))).toList();
    Map<String, ProxyHost> devices = new ConcurrentHashMap<>();
    filteredList.forEach(id -> devices.put(id, createProxyDevice(getHost(), id)));
    ifTrueThen(isExtraDevice(), () -> devices.put(EXTRA_PROXY_DEVICE, makeExtraDevice()));
    return devices;
  }

  ProxyHost createProxyDevice(ManagerHost host, String id);

  ProxyHost makeExtraDevice();

  default void activate() {
    ifNotNullThen(getProxyDevices(), p -> CompletableFuture.runAsync(() -> p.values()
        .parallelStream().forEach(ProxyHost::activate)));
  }

  /**
   * Publish log message for target device.
   */
  default void publishLogMessage(Entry logEntry, String targetId) {
    ifNotNullThen(getProxyDevices(), p ->
          ifNotNullThen(p.getOrDefault(targetId, null), pd ->
                pd.getDeviceManager().publishLogMessage(logEntry, targetId)));
  }

  /**
   * Set device status for target device.
   */
  default void setStatus(Entry report, String targetId) {
    ifNotNullThen(getProxyDevices(), p ->
          ifNotNullThen(p.getOrDefault(targetId, null), pd ->
                pd.getDeviceManager().setStatus(report, targetId)));
  }

  /**
   * Sets gateway status.
   *
   */
  default void setGatewayStatus(String category, Level level, String message) {
    // TODO: Implement a map or tree or something to properly handle different error sources.
    getGatewayState().status = new Entry();
    getGatewayState().status.category = category;
    getGatewayState().status.level = level.value();
    getGatewayState().status.message = message;
    getGatewayState().status.timestamp = getNow();
  }

  /**
   * Updates the state of the gateway.
   */
  default void updateState() {
    updateState(ofNullable((Object) getGatewayState()).orElse(GatewayState.class));
  }

  /**
   * Validates the given gateway family.
   */
  default void validateGatewayFamily(String family, String addr) {
    if (!ProtocolFamily.FAMILIES.contains(family)) {
      throw new IllegalArgumentException(format("Unrecognized address family %s", family));
    }
    String expectedAddr = catchToNull(() -> getMetadata().localnet.families.get(family).addr);
    if (expectedAddr != null && !expectedAddr.equals(addr)) {
      throw new IllegalStateException(
          format("Family address was %s, expected %s", addr, expectedAddr));
    }
  }

  /**
   * Configures the extra device with default settings.
   */
  default void configExtraDevice() {
    Config config = new Config();
    config.pointset = new PointsetConfig();
    config.pointset.points = new HashMap<>();
    PointPointsetConfig pointPointsetConfig = new PointPointsetConfig();
    config.pointset.points.put(EXTRA_PROXY_POINT, pointPointsetConfig);
    getProxyDevices().get(EXTRA_PROXY_DEVICE).configHandler(config);
  }

  /**
   * Update gateway operation based off of a gateway configuration block. This happens in two
   * slightly different forms, one for the gateway proper (primarily indicating what devices
   * should be proxy targets), and the other for the proxy devices themselves.
   */
  default void updateConfig(GatewayConfig gatewayConfig) {
    if (gatewayConfig == null) {
      setGatewayState(null);
      updateState();
      return;
    }
    ifNullThen(getGatewayState(), () -> setGatewayState(new GatewayState()));

    ifNotNullThen(getProxyDevices(),
        p -> ifTrueThen(p.containsKey(EXTRA_PROXY_DEVICE), this::configExtraDevice));

    if (gatewayConfig.proxy_ids == null || gatewayConfig.target != null) {
      try {
        String addr = catchToNull(() -> gatewayConfig.target.addr);
        String family = ofNullable(catchToNull(() -> gatewayConfig.target.family))
                .orElse(ProtocolFamily.VENDOR);
        validateGatewayFamily(family, addr);
        setGatewayStatus(GATEWAY_PROXY_TARGET, Level.DEBUG,
                format("gateway target family %s", family));
      } catch (Exception e) {
        setGatewayStatus(GATEWAY_PROXY_TARGET, Level.ERROR, e.getMessage());
      }
    }
    syncDevices(gatewayConfig);
    updateState();
  }

  void syncDevices(GatewayConfig gatewayConfig);

  void setGatewayState(GatewayState gatewayState);

  default void shutdown() {
    ifNotNullThen(getProxyDevices(), p -> p.values().forEach(ProxyHost::shutdown));
  }

  default void stop() {
    ifNotNullThen(getProxyDevices(), p -> p.values().forEach(ProxyHost::stop));
  }

  default boolean isNoProxy() {
    return false;
  }

  default boolean isExtraDevice() {
    return false;
  }
}
