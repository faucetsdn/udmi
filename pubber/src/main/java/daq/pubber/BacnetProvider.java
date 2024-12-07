package daq.pubber;

import java.util.Map;
import java.util.function.BiConsumer;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.LocalnetManager;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.RefDiscovery;

/**
 * Provides for the bacnet family of stuffs.
 */
public class BacnetProvider extends ManagerBase implements FamilyProvider {

  private final LocalnetManager localnetHost;

  public BacnetProvider(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    localnetHost = (LocalnetManager) host;
  }

  @Override
  public void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publish) {
    FamilyProvider.super.startScan(enumerate, publish);
  }

  @Override
  public void stopScan() {
    FamilyProvider.super.stopScan();
  }

  @Override
  public Map<String, RefDiscovery> enumerateRefs(String addr) {
    return FamilyProvider.super.enumerateRefs(addr);
  }
}
