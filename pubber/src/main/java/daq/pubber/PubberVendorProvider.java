package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static udmi.lib.ProtocolFamily.VENDOR;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.DiscoveryManager;
import udmi.lib.client.LocalnetManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Basic provider for the Vendor protocol family.
 */
public class PubberVendorProvider extends ManagerBase implements PubberFamilyProvider {

  private final LocalnetManager localnetHost;
  private SiteModel siteModel;
  private String selfAddr;

  public PubberVendorProvider(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    localnetHost = (LocalnetManager) host;
  }

  private DiscoveryEvents augmentSend(Entry<String, Metadata> entry, boolean enumerate) {
    String addr = catchToNull(() -> entry.getValue().localnet.families.get(VENDOR).addr);
    DiscoveryEvents event = new DiscoveryEvents();
    event.scan_addr = addr;
    event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(entry.getValue()));
    return event;
  }

  private Map<String, RefDiscovery> getDiscoveredRefs(Metadata entry) {
    Map<String, PointPointsetModel> points = catchToNull(() -> entry.pointset.points);
    return ofNullable(points).orElse(ImmutableMap.of()).entrySet().stream()
        .map(this::pointsetToRef)
        .filter(ref -> nonNull(ref.getValue().point))
        .collect(toMap(Entry::getKey, Entry::getValue));
  }

  private Entry<String, RefDiscovery> pointsetToRef(Entry<String, PointPointsetModel> entry) {
    return new SimpleEntry<>(DiscoveryManager.getVendorRefKey(entry),
        DiscoveryManager.getVendorRefValue(entry));
  }

  private void updateStateAddress() {
    selfAddr = catchToNull(
        () -> siteModel.getMetadata(deviceId).localnet.families.get(VENDOR).addr);
    ifNotNullThen(selfAddr, x -> {
      FamilyLocalnetState stateEntry = new FamilyLocalnetState();
      stateEntry.addr = selfAddr;
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
    requireNonNull(selfAddr, "no local address defined for family " + VENDOR);
    siteModel.forEachMetadata(
        entry -> publisher.accept(entry.getKey(), augmentSend(entry, enumerate)));
  }

  @Override
  public void stopScan() {
  }
}
