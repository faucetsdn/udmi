package daq.pubber;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static daq.pubber.Pubber.configuration;
import static java.lang.String.format;

import udmi.schema.Common.ProtocolFamily;
import udmi.schema.Config;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;

/**
 * Wrapper for a complete device construct.
 */
public class ProxyDevice extends ManagerBase implements ManagerHost {

  public final DeviceManager deviceManager;
  public final Pubber pubberHost;

  /**
   * New instance.
   */
  public ProxyDevice(ManagerHost host, String id) {
    super(host, makeProxyConfiguration(id));
    // Simple shortcut to get access to some foundational mechanisms inside of Pubber.
    pubberHost = (Pubber) host;
    deviceManager = new DeviceManager(this, makeProxyConfiguration(id));
  }

  private static PubberConfiguration makeProxyConfiguration(String id) {
    PubberConfiguration proxyConfiguration = deepCopy(configuration);
    proxyConfiguration.deviceId = id;
    return proxyConfiguration;
  }

  protected void activate() {
    MqttDevice mqttDevice = pubberHost.getMqttDevice(deviceId);
    mqttDevice.registerHandler(MqttDevice.CONFIG_TOPIC, this::configHandler, Config.class);
    mqttDevice.connect(deviceId);
  }

  void configHandler(Config config) {
    pubberHost.configPreprocess(deviceId, config);
    deviceManager.updateConfig(config);
    pubberHost.publisherConfigLog("apply", null, deviceId);
  }

  @Override
  protected void shutdown() {
    deviceManager.shutdown();
  }

  @Override
  protected void stop() {
    deviceManager.stop();
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

  @Override
  public FamilyProvider getLocalnetProvider(ProtocolFamily family) {
    return host.getLocalnetProvider(family);
  }

  public void setMetadata(Metadata metadata) {
    deviceManager.setMetadata(metadata);
  }
}
