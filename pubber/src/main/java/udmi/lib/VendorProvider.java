package udmi.lib;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static udmi.lib.ProtocolFamily.VENDOR;

import com.google.udmi.util.SiteModel;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import udmi.lib.client.DiscoveryManagerClient;
import udmi.lib.client.LocalnetManagerClient;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.Metadata;
import udmi.schema.PubberConfiguration;
import udmi.schema.RefDiscovery;

/**
 * Basic provider for the Vendor protocol family.
 */
public class VendorProvider extends ManagerBase implements FamilyProvider {

  private final LocalnetManagerClient localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  public VendorProvider(ManagerHost host, String family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManagerClient) host;
  }

  private DiscoveryEvents augmentSend(Entry<String, Metadata> entry, boolean enumerate) {
    String addr = catchToNull(() -> entry.getValue().localnet.families.get(VENDOR).addr);
    DiscoveryEvents event = new DiscoveryEvents();
    event.scan_addr = addr;
    event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(entry.getValue()));
    return event;
  }

  private Map<String, RefDiscovery> getDiscoveredRefs(Metadata entry) {
    return entry.pointset.points.entrySet().stream()
        .collect(toMap(DiscoveryManagerClient::getVendorRefKey,
            DiscoveryManagerClient::getVendorRefValue));
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

  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    updateStateAddress();
  }

  @Override
  public void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publisher) {
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata(
        entry -> publisher.accept(entry.getKey(), augmentSend(entry, enumerate)));
  }

  @Override
  public void stopScan() {
  }
}
