package daq.pubber.impl.provider;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
 * Base class for functionality supporting all pubber discovery providers.
 */
public class PubberProviderBase extends ManagerBase {

  private final String family;
  private final LocalnetManager localnetHost;
  private String selfAddr;
  private boolean enumerate;
  private BiConsumer<String, DiscoveryEvents> publisher;
  private Map<String, Metadata> allDevices;
  private SiteModel siteModel;

  /**
   * Create a new instance of a generic family provider.
   */
  public PubberProviderBase(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    this.family = family;
    localnetHost = ((LocalnetManager) host);
  }

  protected void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publisher) {
    this.publisher = publisher;
    this.enumerate = enumerate;
  }

  private DiscoveryEvents augmentSend(String deviceId, boolean enumerate) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = getFamilyAddr(deviceId);
    try {
      event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(getAllDevices().get(deviceId)));
    } catch (Exception e) {
      event.status = new udmi.schema.Entry();
      event.status.level = Level.ERROR.value();
    }
    return event;
  }

  protected Map<String, Metadata> getAllDevices() {
    return allDevices;
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
  private RefDiscovery getModelPointRef(Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = entry.getKey();
    return refDiscovery;
  }

  /**
   * Set the site model.
   */
  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
    selfAddr = getFamilyAddr(deviceId);
    allDevices = siteModel.allMetadata().entrySet().stream()
        .filter(entry -> nonNull(getFamilyAddr(entry.getKey())))
        .collect(toMap(Entry::getKey, Entry::getValue));
    addStateMapEntry();
  }

  private void addStateMapEntry() {
    FamilyLocalnetState stateEntry = new FamilyLocalnetState();
    stateEntry.addr = selfAddr;
    localnetHost.update(family, stateEntry);
  }

  protected Consumer<String> getResultPublisher() {
    return deviceId -> {
      debug(format("Sending %s result for %s@%s", family, deviceId, getFamilyAddr(deviceId)));
      publisher.accept(deviceId, augmentSend(deviceId, enumerate));
    };
  }

  private String getFamilyAddr(String deviceId) {
    return catchToNull(() -> siteModel.getMetadata(deviceId).localnet.families.get(family).addr);
  }

}
