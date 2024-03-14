package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.Common.ProtocolFamily.VENDOR;

import com.google.udmi.util.SiteModel;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
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

  public VendorProvider(ManagerHost host, ProtocolFamily family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManager) host;
  }

  private DiscoveryEvent augmentSend(Entry<String, Metadata> entry, boolean enumerate) {
    String addr = catchToNull(() -> entry.getValue().localnet.families.get(VENDOR).addr);
    debug(format("Discovered device %s has address %s", entry.getKey(), addr));
    DiscoveryEvent event = new DiscoveryEvent();
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
  public void startScan(boolean enumerate, Consumer<DiscoveryEvent> publisher) {
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata(entry -> publisher.accept(augmentSend(entry, enumerate)));
  }

  @Override
  public void stopScan() {
  }
}
