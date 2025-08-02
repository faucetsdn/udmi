package daq.pubber.impl.provider;

import static com.google.api.client.util.Preconditions.checkState;
import static udmi.lib.ProtocolFamily.VENDOR;

import java.util.function.BiConsumer;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Basic provider for the Vendor protocol family.
 */
public class PubberVendorProvider extends PubberProviderBase implements PubberFamilyProvider {

  /**
   * Construct a new instance.
   */
  public PubberVendorProvider(ManagerHost host, String family, String deviceId) {
    super(host, family, deviceId);
    checkState(VENDOR.equals(family), "Incorrect vendor family " + family);
  }

  @Override
  public void startScan(FamilyDiscoveryConfig discoveryConfig,
      BiConsumer<String, DiscoveryEvents> publisher) {
    super.startScan(discoveryConfig, publisher);
    getAllDevices().keySet().forEach(getResultPublisher());
  }

  @Override
  public void stopScan() {
  }
}