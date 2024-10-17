package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullElse;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isGetTrue;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static daq.pubber.Pubber.DEVICE_START_TIME;
import static java.lang.Math.floorMod;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import udmi.schema.Depths;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyDiscoveryState.Phase;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;
import udmi.schema.RefDiscovery;
import udmi.schema.SystemDiscoveryData;

/**
 * Manager wrapper for discovery functionality in pubber.
 */
public class DiscoveryManager extends ManagerBase {

  public static final int SCAN_DURATION_SEC = 10;

  private final DeviceManager deviceManager;
  private DiscoveryState discoveryState;
  private DiscoveryConfig discoveryConfig;
  private SiteModel siteModel;

  public DiscoveryManager(ManagerHost host, PubberConfiguration configuration,
      DeviceManager deviceManager) {
    super(host, configuration);
    this.deviceManager = deviceManager;
  }

  private static boolean shouldEnumerateTo(Depth depth) {
    return ifNullElse(depth, false, d -> switch (d) {
      case ENTRIES, DETAILS -> true;
      default -> false;
    });
  }

  static String getVendorRefKey(Map.Entry<String, PointPointsetModel> entry) {
    return ofNullable(entry.getValue().ref).orElse(entry.getKey());
  }

  static RefDiscovery getVendorRefValue(Map.Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    refDiscovery.possible_values = null;
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = ifNotNullGet(model.ref, entry::getKey);
    return refDiscovery;
  }

  private void updateDiscoveryEnumeration(DiscoveryConfig config) {
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
    Depths depths = config.depths;
    discoveryEvent.refs = maybeEnumerate(depths.refs, () -> enumerateRefs(deviceId));
    discoveryEvent.features = maybeEnumerate(depths.features, SupportedFeatures::getFeatures);
    discoveryEvent.families = maybeEnumerate(depths.families, deviceManager::enumerateFamilies);
    host.publish(discoveryEvent);
  }

  private <K, V> Map<K, V> maybeEnumerate(Depth depth, Supplier<Map<K, V>> supplier) {
    return ifTrueGet(shouldEnumerateTo(depth), supplier);
  }

  private void updateDiscoveryScan(Map<String, FamilyDiscoveryConfig> raw) {
    Map<String, FamilyDiscoveryConfig> families = ofNullable(raw).orElse(ImmutableMap.of());
    ifNullThen(discoveryState.families, () -> discoveryState.families = new HashMap<>());

    discoveryState.families.keySet().stream().filter(not(families::containsKey))
        .forEach(this::removeDiscoveryScan);
    families.keySet().forEach(this::scheduleDiscoveryScan);

    if (raw == null) {
      discoveryState.families = null;
    }
  }

  private void scheduleDiscoveryScan(String family) {
    FamilyDiscoveryConfig familyDiscoveryConfig = getFamilyDiscoveryConfig(family);
    Date rawGeneration = familyDiscoveryConfig.generation;
    int interval = getScanInterval(family);
    if (rawGeneration == null && interval == 0) {
      cancelDiscoveryScan(family, null, null);
      return;
    }

    Date configGeneration = ofNullable(rawGeneration).orElse(DEVICE_START_TIME);
    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    Date baseGeneration = ofNullable(familyDiscoveryState.generation).orElse(DEVICE_START_TIME);

    final Date startGeneration;
    if (interval > 0) {
      Instant now = Instant.now();
      long deltaSec = floorMod(configGeneration.getTime() / 1000 - now.getEpochSecond(), interval);
      startGeneration = Date.from(now.plusSeconds(deltaSec));
    } else if (configGeneration.before(baseGeneration)) {
      cancelDiscoveryScan(family, configGeneration, STOPPED);
      return;
    } else {
      startGeneration = configGeneration;
    }

    if (startGeneration.equals(baseGeneration)) {
      return;
    }

    info("Discovery scan generation " + family + " pending at " + isoConvert(startGeneration));
    familyDiscoveryState.generation = startGeneration;
    familyDiscoveryState.phase = PENDING;
    updateState();

    scheduleFuture(startGeneration, () -> checkDiscoveryScan(family, startGeneration));
  }

  private FamilyDiscoveryConfig getFamilyDiscoveryConfig(String family) {
    return discoveryConfig.families.get(family);
  }

  private void removeDiscoveryScan(String family) {
    FamilyDiscoveryState removed = discoveryState.families.remove(family);
    ifNotNullThen(removed, was -> cancelDiscoveryScan(family, was.generation, STOPPED));
  }

  private void cancelDiscoveryScan(String family, Date configGeneration, Phase phase) {
    FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState(family);
    info(format("Discovery scan %s phase %s as %s", family, phase, isoConvert(configGeneration)));
    familyDiscoveryState.phase = phase;
    familyDiscoveryState.generation = configGeneration;
    updateState();
  }

  private FamilyDiscoveryState getFamilyDiscoveryState(String family) {
    return discoveryState.families.get(family);
  }

  private FamilyDiscoveryState ensureFamilyDiscoveryState(String family) {
    if (discoveryState.families == null) {
      // If there is no need for family state, then return a floating bucket for results.
      return new FamilyDiscoveryState();
    }
    return discoveryState.families.computeIfAbsent(
        family, key -> new FamilyDiscoveryState());
  }

  private void checkDiscoveryScan(String family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      ifTrueThen(familyDiscoveryState.phase == PENDING,
          () -> startDiscoveryScan(family, scanGeneration));
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  private void startDiscoveryScan(String family, Date scanGeneration) {
    info("Discovery scan starting " + family + " as " + isoConvert(scanGeneration));
    Date stopTime = Date.from(scanGeneration.toInstant().plusSeconds(SCAN_DURATION_SEC));
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

  private boolean shouldEnumerate(String family) {
    return shouldEnumerateTo(getFamilyDiscoveryConfig(family).depth);
  }

  private FamilyProvider discoveryProvider(String family) {
    return host.getLocalnetProvider(family);
  }

  private FamilyDiscovery eventForTarget(Map.Entry<String, FamilyLocalnetModel> target) {
    FamilyDiscovery event = new FamilyDiscovery();
    event.addr = target.getValue().addr;
    return event;
  }

  private FamilyLocalnetModel getFamilyLocalnetModel(String family,
      Metadata targetMetadata) {
    try {
      return targetMetadata.localnet.families.get(family);
    } catch (Exception e) {
      return null;
    }
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

  private int getScanInterval(String family) {
    return ofNullable(
        catchToNull(() -> getFamilyDiscoveryConfig(family).scan_interval_sec)).orElse(0);
  }

  private <T> T ifTrue(Boolean condition, Supplier<T> supplier) {
    return isGetTrue(() -> condition) ? supplier.get() : null;
  }

  private Map<String, RefDiscovery> enumerateRefs(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points.entrySet().stream()
        .collect(toMap(DiscoveryManager::getVendorRefKey, DiscoveryManager::getVendorRefValue));
  }

  private void updateState() {
    updateState(ofNullable((Object) discoveryState).orElse(DiscoveryState.class));
  }

  /**
   * Update the discovery config.
   */
  public void updateConfig(DiscoveryConfig discovery) {
    discoveryConfig = discovery;
    if (discovery == null) {
      discoveryState = null;
      updateState();
      return;
    }
    if (discoveryState == null) {
      discoveryState = new DiscoveryState();
    }
    updateDiscoveryEnumeration(discovery);
    updateDiscoveryScan(discovery.families);
    updateState();
  }

  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
  }
}
