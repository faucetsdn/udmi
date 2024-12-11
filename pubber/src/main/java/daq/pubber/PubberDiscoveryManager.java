package daq.pubber;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static daq.pubber.PubberUdmiPublisher.DEVICE_START_TIME;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.udmi.util.SiteModel;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.lib.client.DiscoveryManager;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerations;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;
import udmi.schema.RefDiscovery;
import udmi.schema.SystemDiscoveryData;

/**
 * Manager wrapper for discovery functionality in pubber.
 */
public class PubberDiscoveryManager extends PubberManager implements DiscoveryManager {

  public static final int SCAN_DURATION_SEC = 10;

  private final PubberDeviceManager deviceManager;
  private DiscoveryState discoveryState;
  private DiscoveryConfig discoveryConfig;
  private SiteModel siteModel;

  public PubberDiscoveryManager(ManagerHost host, PubberConfiguration configuration,
      PubberDeviceManager deviceManager) {
    super(host, configuration);
    this.deviceManager = deviceManager;
  }

  /**
   * Get a ref value that describes a point for self enumeration.
   */
  public static RefDiscovery getModelPointRef(Entry<String, PointPointsetModel> entry,
      boolean swapPointRef) {
    RefDiscovery refDiscovery = new RefDiscovery();
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = swapPointRef ? model.ref : entry.getKey();
    return refDiscovery;
  }

  /**
   * Updates discovery enumeration.
   *
   * @param config Discovery Configuration.
   */
  public void updateDiscoveryEnumeration(DiscoveryConfig config) {
    Date enumerationGeneration = config.generation;
    if (enumerationGeneration == null) {
      discoveryState.generation = null;
      return;
    }
    if (discoveryState.generation != null
        && !enumerationGeneration.after(discoveryState.generation)) {
      return;
    }
    discoveryState.generation = enumerationGeneration;
    info("Discovery enumeration at " + isoConvert(enumerationGeneration));
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.generation = enumerationGeneration;
    Enumerations depths = config.enumerations;
    discoveryEvent.points = maybeEnumerate(depths.refs, () -> enumeratePoints(deviceId));
    discoveryEvent.features = maybeEnumerate(depths.features, PubberFeatures::getFeatures);
    discoveryEvent.families = maybeEnumerate(depths.families, deviceManager::enumerateFamilies);
    host.publish(discoveryEvent);
  }

  /**
   * Starts a discovery scan.
   *
   * @param family Discovery scan family.
   * @param scanGeneration Scan generation.
   */
  public void startDiscoveryScan(String family, Date scanGeneration) {
    info("Discovery scan starting " + family + " generation " + isoConvert(scanGeneration));
    Date stopTime = Date.from(getNowInstant().plusSeconds(SCAN_DURATION_SEC));
    final FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    scheduleFuture(stopTime, () -> discoveryScanComplete(family, scanGeneration));
    familyDiscoveryState.generation = scanGeneration;
    familyDiscoveryState.phase = ACTIVE;
    AtomicInteger sendCount = new AtomicInteger();
    familyDiscoveryState.active_count = sendCount.get();
    updateState();
    discoveryProvider(family).startScan(shouldEnumerate(family),
        (deviceId, discoveryEvent) -> ifNotNullThen(discoveryEvent.scan_addr, addr -> {
          info(format("Discovered %s device %s for gen %s", family, addr,
              isoConvert(scanGeneration)));
          discoveryEvent.scan_family = family;
          discoveryEvent.generation = scanGeneration;
          discoveryEvent.system = new SystemDiscoveryData();
          discoveryEvent.system.ancillary = new HashMap<>();
          discoveryEvent.system.ancillary.put("device-name", deviceId);
          familyDiscoveryState.active_count = sendCount.incrementAndGet();
          updateState();
          host.publish(discoveryEvent);
        }));
  }

  private FamilyProvider discoveryProvider(String family) {
    return host.getLocalnetProvider(family);
  }

  private void discoveryScanComplete(String family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      ifTrueThen(scanGeneration.equals(familyDiscoveryState.generation),
          () -> {
            discoveryProvider(family).stopScan();
            familyDiscoveryState.phase = STOPPED;
            updateState();
            scheduleDiscoveryScan(family);
          });
    } catch (Exception e) {
      throw new RuntimeException("While completing discovery scan " + family, e);
    }
  }

  @Override
  public Date getDeviceStartTime() {
    return DEVICE_START_TIME;
  }

  private Map<String, RefDiscovery> enumeratePoints(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points.entrySet().stream()
        .collect(toMap(Entry::getKey, entry -> getModelPointRef(entry, true)));
  }

  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
  }

  @Override
  public DiscoveryState getDiscoveryState() {
    return discoveryState;
  }

  @Override
  public void setDiscoveryState(DiscoveryState discoveryState) {
    this.discoveryState = discoveryState;
  }

  @Override
  public DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  @Override
  public void setDiscoveryConfig(DiscoveryConfig discoveryConfig) {
    this.discoveryConfig = discoveryConfig;
  }
}
