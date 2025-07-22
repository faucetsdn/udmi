package daq.pubber.impl.provider;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.VENDOR;

import com.google.udmi.util.SiteModel;
import java.util.function.BiConsumer;
import udmi.lib.client.manager.LocalnetManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyLocalnetState;

/**
 * Basic provider for the Vendor protocol family.
 */
public class PubberVendorProvider extends PubberProviderBase implements PubberFamilyProvider {

  private final LocalnetManager localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  /**
   * Construct a new instance.
   */
  public PubberVendorProvider(ManagerHost host, String family, String deviceId) {
    super(host, family, deviceId);
    checkState(VENDOR.equals(family), "Incorrect vendor family " + family);
    localnetHost = (LocalnetManager) host;
  }

  private void updateStateAddress() {
    selfAddr = catchToNull(
        () -> siteModel.getMetadata(deviceId).localnet.families.get(VENDOR).addr);
    ifNotNullThen(selfAddr, addr -> {
      FamilyLocalnetState stateEntry = new FamilyLocalnetState();
      stateEntry.addr = addr;
      localnetHost.update(VENDOR, stateEntry);
    });
  }

  @Override
  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    updateStateAddress();
  }

  @Override
  public void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publisher) {
    requireNonNull(selfAddr, format("No local address defined for family %s", VENDOR));
    siteModel.forEachMetadata(e -> maybeSendResult(e, publisher, enumerate));
  }

  @Override
  public void stopScan() {
  }
}