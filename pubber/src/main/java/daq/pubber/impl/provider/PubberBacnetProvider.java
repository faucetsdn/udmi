package daq.pubber.impl.provider;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static udmi.lib.ProtocolFamily.BACNET;

import com.google.udmi.util.SiteModel;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.manager.LocalnetManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Provides for the bacnet family of stuffs.
 */
public class PubberBacnetProvider extends ManagerBase implements PubberFamilyProvider {

  private static final int BACNET_DISCOVERY_RATE_SEC = 1;
  private final LocalnetManager localnetHost;
  private final Deque<String> toReport = new ArrayDeque<>();
  private Map<String, Metadata> bacnetDevices;
  private BiConsumer<String, DiscoveryEvents> publisher;
  private boolean enumerate;

  /**
   * Provider for metadata-based (simulated) bacnet discovery.
   */
  public PubberBacnetProvider(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    checkState(family.equals(BACNET));
    localnetHost = ((LocalnetManager) host);
  }

  private void addStateMapEntry() {
    FamilyLocalnetState stateEntry = new FamilyLocalnetState();
    stateEntry.addr = getBacnetAddr(getMetadata(deviceId));
    localnetHost.update(BACNET, stateEntry);
  }

  @Override
  public synchronized void periodicUpdate() {
    ifNotNullThen(toReport.poll(), deviceId -> {
      debug(format("Sending bacnet result for %s@%s", deviceId, getBacnetAddr(deviceId)));
      publisher.accept(deviceId, augmentSend(deviceId, enumerate));
    });
  }

  private DiscoveryEvents augmentSend(String deviceId, boolean enumerate) {
    String addr = getBacnetAddr(getMetadata(deviceId));
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = addr;
    try {
      event.refs = ifTrueGet(enumerate, () -> enumerateRefs(deviceId));
    } catch (Exception e) {
      event.status = new udmi.schema.Entry();
      event.status.level = Level.ERROR.value();
    }
    return event;
  }

  private Metadata getMetadata(String deviceId) {
    return bacnetDevices.get(deviceId);
  }

  private String getBacnetAddr(String deviceId) {
    return getBacnetAddr(getMetadata(deviceId));
  }

  private String getBacnetAddr(Metadata metadata) {
    return catchToNull(() -> metadata.localnet.families.get(BACNET).addr);
  }

  @Override
  public synchronized void startScan(boolean enumerate,
      BiConsumer<String, DiscoveryEvents> publisher) {
    toReport.clear();
    toReport.addAll(bacnetDevices.keySet());
    this.publisher = publisher;
    this.enumerate = enumerate;
    updateInterval(BACNET_DISCOVERY_RATE_SEC);
  }

  @Override
  public synchronized void stopScan() {
    super.stop();
  }

  @Override
  public Map<String, RefDiscovery> enumerateRefs(String addr) {
    HashMap<String, PointPointsetModel> points = catchToElse(
        () -> bacnetDevices.get(addr).pointset.points, new HashMap<>());
    return points.entrySet().stream()
        .filter(entry -> entry.getValue().ref != null)
        .map(this::pointToRef)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private Entry<String, RefDiscovery> pointToRef(Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    refDiscovery.point = entry.getKey();
    String refKey = requireNonNull(entry.getValue().ref, "missing point ref");
    return new AbstractMap.SimpleEntry<>(refKey, refDiscovery);
  }

  @Override
  public void setSiteModel(SiteModel siteModel) {
    bacnetDevices = siteModel.allMetadata().entrySet().stream()
        .filter(entry -> nonNull(getBacnetAddr(entry.getValue())))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    addStateMapEntry();
  }
}
