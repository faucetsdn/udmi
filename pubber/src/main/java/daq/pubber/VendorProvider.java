package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.util.Objects.requireNonNull;
import static udmi.schema.Common.ProtocolFamily.VENDOR;

import com.google.udmi.util.SiteModel;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.PubberConfiguration;

public class VendorProvider extends ManagerBase implements LocalnetProvider {

  private final LocalnetManager localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  public VendorProvider(ManagerHost host, ProtocolFamily family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManager) host;
  }

  private void updateStateAddress() {
    selfAddr = catchToNull(
        () -> siteModel.getMetadata(config.deviceId).localnet.families.get(VENDOR).addr);
    ifNotNullThen(selfAddr, x -> {
      FamilyLocalnetState stateEntry = new FamilyLocalnetState();
      stateEntry.addr = selfAddr;
      localnetHost.update(VENDOR, stateEntry);
    });
  }

  void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    updateStateAddress();
  }

  @Override
  public void startScan(DiscoveryEvent event) {
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata((id, metadata) -> {
      event.scan_addr = catchToNull(() -> metadata.localnet.families.get(VENDOR).addr);
      ifNotNullThen(event.scan_addr, () -> host.publish(event));
    });
  }

  @Override
  public void stopScan() {
  }
}
