package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static udmi.schema.Common.ProtocolFamily.VENDOR;

import com.google.udmi.util.SiteModel;
import java.util.function.Consumer;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.PubberConfiguration;

/**
 * Basic provider for the Vendor protocol family.
 */
public class VendorProvider extends ManagerBase implements FamilyProvider {

  private final LocalnetManager localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  public VendorProvider(ManagerHost host, ProtocolFamily family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManager) host;
  }

  private DiscoveryEvent augmentSend(String id, String scanAddr) {
    debug(format("Discovered device %s has address %s", id, scanAddr));
    DiscoveryEvent event = new DiscoveryEvent();
    event.scan_addr = scanAddr;
    return event;
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
  public void startScan(Consumer<DiscoveryEvent> publisher) {
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata((id, metadata) -> publisher.accept(augmentSend(id,
        catchToNull(() -> metadata.localnet.families.get(VENDOR).addr))));
  }

  @Override
  public void stopScan() {
  }
}
