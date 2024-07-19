package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static daq.pubber.ProtocolFamily.VENDOR;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.udmi.util.SiteModel;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.Metadata;
import udmi.schema.PointDiscovery;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;

/**
 * Basic provider for the Vendor protocol family.
 */
public class VendorProvider extends ManagerBase implements FamilyProvider {

  private final LocalnetManager localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  public VendorProvider(ManagerHost host, String family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManager) host;
  }

  private DiscoveryEvents augmentSend(Entry<String, Metadata> entry, boolean enumerate) {
    String addr = catchToNull(() -> entry.getValue().localnet.families.get(VENDOR).addr);
    DiscoveryEvents event = new DiscoveryEvents();
    event.scan_addr = addr;
    event.points = ifTrueGet(enumerate, () -> getDiscoverPoints(entry.getValue()));
    return event;
  }

  private Map<String, PointDiscovery> getDiscoverPoints(Metadata entry) {
    return entry.pointset.points.entrySet().stream()
        .collect(toMap(Entry::getKey, this::makePointDiscovery));
  }

  private PointDiscovery makePointDiscovery(Entry<String, PointPointsetModel> entry) {
    return new PointDiscovery();
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
  public void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publisher) {
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata(
        entry -> publisher.accept(entry.getKey(), augmentSend(entry, enumerate)));
  }

  @Override
  public void stopScan() {
  }
}
