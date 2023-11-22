package daq.pubber;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static daq.pubber.Pubber.configuration;
import static java.lang.String.format;

import udmi.schema.Config;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class ProxyDevice extends ManagerBase implements ManagerHost {

  private final DeviceManager deviceManager;
  private final Pubber pubberHost;

  /**
   * New instance.
   */
  public ProxyDevice(Pubber host, String id) {
    super(host, makeProxyConfiguration(id));
    pubberHost = host;
    deviceManager = new DeviceManager(host, makeProxyConfiguration(id));
  }

  private static PubberConfiguration makeProxyConfiguration(String id) {
    PubberConfiguration proxyConfiguration = deepCopy(configuration);
    proxyConfiguration.deviceId = id;
    return proxyConfiguration;
  }

  protected void initialize() {
    MqttDevice mqttDevice = pubberHost.getMqttDevice(deviceId);
    mqttDevice.registerHandler(MqttDevice.CONFIG_TOPIC, this::configHandler, Config.class);
    mqttDevice.connect(deviceId);
  }

  void configHandler(Config config) {
    info(format("Proxy %s config handler", deviceId));
    pubberHost.configPreprocess(deviceId, config);
    deviceManager.updateConfig(config);
  }

  protected void shutdown() {
    deviceManager.shutdown();
  }

  @Override
  public void publish(Object message) {
    pubberHost.publish(deviceId, message);
  }

  @Override
  public void update(Object update) {
    String simpleName = update.getClass().getSimpleName();
    warn(format("Ignoring proxy device %s update for %s", deviceId, simpleName));
  }

}
