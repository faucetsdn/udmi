package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import udmi.lib.ManagerHost;
import udmi.lib.ProtocolFamily;
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
 * Gateway client.
 */
public interface GatewayManager extends Manager {

  String EXTRA_PROXY_DEVICE = "XXX-1";
  String EXTRA_PROXY_POINT = "xxx_conflagration";
  Metadata getMetadata();

  void setMetadata(Metadata metadata);

  GatewayState getGatewayState();

  Map<String, ProxyDeviceHost> getProxyDevices();

  /**
   * Creates a map of proxy devices.
   *
   * @param proxyIds A list of device IDs to create proxies for.
   * @return A map where each key-value pair represents a device ID and its corresponding proxy
   * @throws NoSuchElementException if no first element exists in the stream
   */
  default Map<String, ProxyDeviceHost> createProxyDevices(List<String> proxyIds) {
    if (proxyIds == null) {
      return Map.of();
    }

    Map<String, ProxyDeviceHost> devices = new HashMap<>();

    String firstId = proxyIds.stream().sorted().findFirst().orElse(null);
    String noProxyId = ifTrueGet(isTrue(getOptions().noProxy), () -> firstId);
    ifNotNullThen(noProxyId, id -> warn(format("Not proxying device %s", noProxyId)));
    proxyIds.stream().filter(not(id -> id.equals(noProxyId)))
        .forEach(id -> devices.put(id, createProxyDevice(getHost(), id, getConfig())));

    ifTrueThen(getOptions().extraDevice, () -> devices.put(EXTRA_PROXY_DEVICE, makeExtraDevice()));

    return devices;
  }

  ProxyDeviceHost createProxyDevice(ManagerHost host, String id,
      PubberConfiguration config);

  ProxyDeviceHost makeExtraDevice();

  default void activate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  /**
   * Publish log message for target device.
   */
  default void publishLogMessage(Entry logEntry, String targetId) {
    ifNotNullThen(getProxyDevices(), p -> p.values().forEach(pd -> {
      if (pd.getDeviceId().equals(targetId)) {
        pd.getDeviceManager().publishLogMessage(logEntry, targetId);
      }
    }));
  }

  /**
   * Sets gateway status.
   *
   * @param category Category.
   * @param level Level.
   * @param message Message.
   */
  default void setGatewayStatus(String category, Level level, String message) {
    // TODO: Implement a map or tree or something to properly handle different error sources.
    getGatewayState().status = new Entry();
    getGatewayState().status.category = category;
    getGatewayState().status.level = level.value();
    getGatewayState().status.message = message;
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
      throw new IllegalArgumentException("Unrecognized address family " + family);
    }

    String expectedAddr = catchToNull(() -> getMetadata().localnet.families.get(family).addr);

    if (expectedAddr != null && !expectedAddr.equals(addr)) {
      throw new IllegalStateException(
          format("Family address was %s, expected %s", addr, expectedAddr));
    }
  }

  /**
   * Configures the extra device with default settings.
   *
   */
  default void configExtraDevice() {
    Config config = new Config();
    config.pointset = new PointsetConfig();
    config.pointset.points = new HashMap<>();
    PointPointsetConfig pointPointsetConfig = new PointPointsetConfig();
    config.pointset.points.put(EXTRA_PROXY_POINT, pointPointsetConfig);
    getProxyDevices().get(EXTRA_PROXY_DEVICE).configHandler(config);
  }

  void updateConfig(GatewayConfig gateway);

  void setSiteModel(SiteModel siteModel);

}
