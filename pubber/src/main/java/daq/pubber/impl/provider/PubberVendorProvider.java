package daq.pubber.impl.provider;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
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
import udmi.lib.client.manager.LocalnetManager;
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
    event.addr = addr;
    event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(entry.getValue()));
    return event;
  }

  private Map<String, RefDiscovery> getDiscoveredRefs(Metadata entry) {
    Map<String, PointPointsetModel> points = catchToNull(() -> entry.pointset.points);
    return ofNullable(points).orElse(ImmutableMap.of()).entrySet().stream()
        .filter(point -> nonNull(point.getValue().ref))
        .map(this::pointsetToRef)
        .collect(toMap(Entry::getKey, Entry::getValue));
  }

  private Entry<String, RefDiscovery> pointsetToRef(Entry<String, PointPointsetModel> e) {
    return new SimpleEntry<>(e.getValue().ref, getModelPointRef(e));
  }

  /**
   * Get a ref value that describes a point for self enumeration.
   */
  private static RefDiscovery getModelPointRef(Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = entry.getKey();
    return refDiscovery;
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
    siteModel.forEachMetadata(e -> publisher.accept(e.getKey(), augmentSend(e, enumerate)));
  }

  @Override
  public void stopScan() {
  }
}
