package daq.pubber;

import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.isGetTrue;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static daq.pubber.Pubber.DEVICE_START_TIME;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.SiteModel;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerate;
import udmi.schema.FamilyDiscovery;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.Metadata;
import udmi.schema.PointDiscovery;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;

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
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.generation = enumerationGeneration;
    Enumerate enumerate = config.enumerate;
    discoveryEvent.points = ifTrue(enumerate.points, () -> enumeratePoints(deviceId));
    discoveryEvent.features = ifTrue(enumerate.features, SupportedFeatures::getFeatures);
    discoveryEvent.families = ifTrue(enumerate.families, deviceManager::enumerateFamilies);
    host.publish(discoveryEvent);
  }

  private void updateDiscoveryScan(Map<ProtocolFamily, FamilyDiscoveryConfig> raw) {
    Map<ProtocolFamily, FamilyDiscoveryConfig> families = ofNullable(raw).orElse(ImmutableMap.of());
    ifNullThen(discoveryState.families, () -> discoveryState.families = new HashMap<>());

    discoveryState.families.keySet().stream().filter(not(families::containsKey))
        .forEach(this::removeDiscoveryScan);
    families.keySet().forEach(this::scheduleDiscoveryScan);

    if (raw == null) {
      discoveryState.families = null;
    }
  }

  private boolean scheduleDiscoveryScan(ProtocolFamily family) {
    FamilyDiscoveryConfig familyDiscoveryConfig = discoveryConfig.families.get(family);
    Date configGeneration = familyDiscoveryConfig.generation;
    if (configGeneration == null) {
      cancelDiscoveryScan(family);
      return false;
    }

    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    Date previousGeneration = familyDiscoveryState.generation;
    Date baseGeneration = previousGeneration == null ? DEVICE_START_TIME : previousGeneration;
    final Date startGeneration;
    if (configGeneration.before(baseGeneration)) {
      int interval = getScanInterval(family);
      if (interval > 0) {
        long deltaSec = (baseGeneration.getTime() - configGeneration.getTime() + 999) / 1000;
        long intervals = (deltaSec + interval - 1) / interval;
        startGeneration = Date.from(
            configGeneration.toInstant().plusSeconds(intervals * interval));
      } else {
        startGeneration = null;
      }
    } else {
      startGeneration = configGeneration;
    }

    info("Discovery scan generation " + family + " future is " + isoConvert(startGeneration));
    familyDiscoveryState.generation = startGeneration;
    familyDiscoveryState.active = false;
    updateState();

    ifNotNullThen(startGeneration, start -> scheduleFuture(start, () -> checkDiscoveryScan(family, start)));
    return startGeneration != null;
  }

  private void removeDiscoveryScan(ProtocolFamily family) {
    cancelDiscoveryScan(family);
    discoveryState.families.remove(family);
    updateState();
  }

  private void cancelDiscoveryScan(ProtocolFamily family) {
    FamilyDiscoveryState familyDiscoveryState = discoveryState.families.get(family);
    if (familyDiscoveryState != null && familyDiscoveryState.active) {
      info("Canceling scheduled discovery family " + family);
      familyDiscoveryState.active = null;
      familyDiscoveryState.generation = null;
    }
    updateState();
  }

  private FamilyDiscoveryState ensureFamilyDiscoveryState(ProtocolFamily family) {
    return discoveryState.families.computeIfAbsent(
        family, key -> new FamilyDiscoveryState());
  }

  private void checkDiscoveryScan(ProtocolFamily family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      ifNotTrueThen(familyDiscoveryState.active, () -> startDiscoveryScan(family, scanGeneration));
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan start", e);
    }
  }

  private void startDiscoveryScan(ProtocolFamily family, Date scanGeneration) {
    info("Discovery scan starting " + family + " as " + isoConvert(scanGeneration));
    Date stopTime = Date.from(Instant.now().plusSeconds(SCAN_DURATION_SEC));
    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    scheduleFuture(stopTime, () -> discoveryScanComplete(family, scanGeneration));
    familyDiscoveryState.generation = scanGeneration;
    familyDiscoveryState.active = true;
    updateState();
    Date sendTime = Date.from(Instant.now().plusSeconds(SCAN_DURATION_SEC / 2));

    scheduleFuture(sendTime, () -> sendDiscoveryEvent(family, scanGeneration));
  }

  private void sendDiscoveryEvent(ProtocolFamily family, Date scanGeneration) {
    FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
    if (scanGeneration.equals(familyDiscoveryState.generation)
        && familyDiscoveryState.active) {
      AtomicInteger sentEvents = new AtomicInteger();
      siteModel.forEachMetadata((deviceId, targetMetadata) -> {
        FamilyLocalnetModel familyLocalnetModel = getFamilyLocalnetModel(family, targetMetadata);
        if (familyLocalnetModel != null && familyLocalnetModel.addr != null) {
          DiscoveryEvent discoveryEvent = new DiscoveryEvent();
          discoveryEvent.generation = scanGeneration;
          discoveryEvent.scan_family = family;
          discoveryEvent.scan_addr = deviceId;
          discoveryEvent.families = targetMetadata.localnet.families.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, this::eventForTarget));
          discoveryEvent.families.computeIfAbsent(ProtocolFamily.IOT,
              key -> new FamilyDiscovery()).addr = deviceId;
          if (isGetTrue(() -> discoveryConfig.families.get(family).enumerate)) {
            discoveryEvent.points = enumeratePoints(deviceId);
          }
          host.publish(discoveryEvent);
          sentEvents.incrementAndGet();
        }
      });
      info("Sent " + sentEvents.get() + " discovery events from " + family + " for "
          + scanGeneration);
    }
  }

  private FamilyDiscovery eventForTarget(Map.Entry<ProtocolFamily, FamilyLocalnetModel> target) {
    FamilyDiscovery event = new FamilyDiscovery();
    event.addr = target.getValue().addr;
    return event;
  }

  private FamilyLocalnetModel getFamilyLocalnetModel(ProtocolFamily family,
      Metadata targetMetadata) {
    try {
      return targetMetadata.localnet.families.get(family);
    } catch (Exception e) {
      return null;
    }
  }

  private void discoveryScanComplete(ProtocolFamily family, Date scanGeneration) {
    try {
      FamilyDiscoveryState familyDiscoveryState = ensureFamilyDiscoveryState(family);
      if (scanGeneration.equals(familyDiscoveryState.generation)) {
        if (!scheduleDiscoveryScan(family)) {
          info("Discovery scan complete " + family + " as " + isoConvert(scanGeneration));
          familyDiscoveryState.active = false;
          updateState();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While checking for discovery scan complete", e);
    }
  }

  private int getScanInterval(ProtocolFamily family) {
    return catchToElse(() -> discoveryConfig.families.get(family).scan_interval_sec, 0);
  }

  private <T> T ifTrue(Boolean condition, Supplier<T> supplier) {
    return isGetTrue(() -> condition) ? supplier.get() : null;
  }

  private Map<String, PointDiscovery> enumeratePoints(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points.entrySet().stream().collect(
        Collectors.toMap(this::getPointUniqKey, this::getPointDiscovery));
  }

  private String getPointUniqKey(Map.Entry<String, PointPointsetModel> entry) {
    return format("%08x", entry.getKey().hashCode());
  }

  private PointDiscovery getPointDiscovery(
      Map.Entry<String, PointPointsetModel> entry) {
    PointDiscovery pointDiscovery = new PointDiscovery();
    PointPointsetModel model = entry.getValue();
    pointDiscovery.writable = model.writable;
    pointDiscovery.units = model.units;
    pointDiscovery.ref = model.ref;
    pointDiscovery.name = entry.getKey();
    return pointDiscovery;
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
