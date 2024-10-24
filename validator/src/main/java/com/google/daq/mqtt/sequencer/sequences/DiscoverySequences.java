package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.symmetricDifference;
import static com.google.daq.mqtt.sequencer.sequences.DiscoverySequences.ScanMode.NO_ENUMERATION;
import static com.google.daq.mqtt.sequencer.sequences.DiscoverySequences.ScanMode.NO_SCAN;
import static com.google.daq.mqtt.sequencer.sequences.DiscoverySequences.ScanMode.PLEASE_ENUMERATE;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.cleanInstantDate;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNullElse;
import static com.google.udmi.util.GeneralUtils.joinOrNull;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static udmi.schema.Bucket.DISCOVERY_SCAN;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
import static udmi.schema.Depths.Depth.ENTRIES;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.BETA;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;
import static udmi.schema.FeatureDiscovery.FeatureStage.STABLE;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import udmi.lib.ProtocolFamily;
import udmi.schema.Bucket;
import udmi.schema.Depths;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequenceBase {

  public static final Duration SCAN_START_DELAY = Duration.ofSeconds(10);
  public static final int SCAN_START_DELAY_SEC = (int) SCAN_START_DELAY.getSeconds();
  public static final int SCAN_START_JITTER_SEC = 3;
  private static final int SCAN_ITERATIONS = 2;
  private static final String scanFamily = ProtocolFamily.VENDOR;
  private static final Date LONG_TIME_AGO = Date.from(Instant.parse("2020-10-18T12:02:01Z"));
  private Set<String> metaFamilies;
  private Instant scanGeneration;

  private static boolean isActive(Entry<String, FeatureDiscovery> entry) {
    return ofNullable(entry.getValue().stage).orElse(STABLE).compareTo(BETA) >= 0;
  }

  private static Depth enumerationDepthIf(ScanMode shouldEnumerate) {
    return shouldEnumerate == PLEASE_ENUMERATE ? ENTRIES : null;
  }

  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("discovery");
  }

  private DiscoveryEvents runEnumeration(Depths depths) {
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.depths = depths;
    untilTrue("enumeration not active", () -> deviceState.discovery.generation == null);

    Date startTime = SemanticDate.describe("generation start time", cleanDate());
    deviceConfig.discovery.generation = startTime;
    info("Starting empty enumeration at " + isoConvert(startTime));
    untilTrue("matching enumeration generation",
        () -> deviceState.discovery.generation.equals(startTime));

    deviceConfig.discovery.generation = null;
    untilTrue("cleared enumeration generation", () -> deviceState.discovery.generation == null);

    List<DiscoveryEvents> allEvents = popReceivedEvents(DiscoveryEvents.class);
    // Filter for enumeration events, since there will sometimes be lingering scan events.
    List<DiscoveryEvents> enumEvents = allEvents.stream().filter(event -> event.scan_addr == null)
        .toList();
    assertEquals("a single discovery event received", 1, enumEvents.size());
    DiscoveryEvents event = enumEvents.get(0);
    info("Received discovery generation " + isoConvert(event.generation));
    assertEquals("matching event generation", startTime, event.generation);
    return event;
  }

  private void checkSelfEnumeration(DiscoveryEvents event, Depths depths) {
    if (shouldEnumerate(depths.families)) {
      Set<String> models = ofNullable(deviceMetadata.localnet)
          .map(localnet -> localnet.families.keySet()).orElse(null);
      Set<String> events = ofNullable(event.families).map(Map::keySet)
          .orElse(null);
      checkThat("family enumeration matches", () -> models.size() == events.size());
    } else {
      checkThat("no family enumeration exists", () -> event.families == null);
    }

    if (shouldEnumerate(depths.features)) {
      checkFeatureDiscovery(event.features);
    } else {
      checkThat("no feature enumeration exists", () -> event.features == null);
    }

    if (shouldEnumerate(depths.refs)) {
      int expectedSize = ofNullable(deviceMetadata.pointset.points).map(HashMap::size)
          .orElse(0);
      checkThat("enumerated point count matches", () -> event.refs.size() == expectedSize);
    } else {
      checkThat("no point enumeration exists", () -> event.refs == null);
    }
  }

  private boolean shouldEnumerate(Depth depth) {
    return ifNullElse(depth, false, d -> switch (d) {
      default -> false;
      case ENTRIES, DETAILS -> true;
    });
  }

  private void checkFeatureDiscovery(Map<String, FeatureDiscovery> features) {
    Set<String> enumeratedFeatures = features.entrySet().stream()
        .filter(DiscoverySequences::isActive).map(Entry::getKey).collect(Collectors.toSet());
    requireNonNull(deviceMetadata.features, "device metadata features missing");
    Set<String> enabledFeatures = deviceMetadata.features.entrySet().stream()
        .filter(DiscoverySequences::isActive).map(Entry::getKey).collect(Collectors.toSet());
    SetView<String> extraFeatures = Sets.difference(enumeratedFeatures, enabledFeatures);
    SetView<String> missingFeatures = Sets.difference(enabledFeatures, enumeratedFeatures);
    SetView<String> difference = Sets.union(extraFeatures, missingFeatures);
    String details = format("missing { %s }, extra { %s }", CSV_JOINER.join(missingFeatures),
        CSV_JOINER.join(extraFeatures));
    checkThat("feature enumeration matches metadata", difference::isEmpty, details);
    Set<String> unofficial = enumeratedFeatures.stream()
        .filter(feature -> !Bucket.contains(feature)).collect(Collectors.toSet());
    String format = format("unrecognized { %s }", CSV_JOINER.join(unofficial));
    checkThat("all enumerated features are official buckets", unofficial::isEmpty, format);
  }

  private boolean isTrue(Boolean condition) {
    return ofNullable(condition).orElse(false);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION, stage = PREVIEW)
  @Summary("Check enumeration of nothing at all")
  public void enumerate_nothing() {
    Depths enumerate = new Depths();
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = ALPHA)
  @Summary("Check enumeration of device points")
  public void enumerate_pointset() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      skipTest("No metadata pointset points defined");
    }
    Depths enumerate = new Depths();
    enumerate.refs = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_FEATURES, stage = PREVIEW)
  @Summary("Check enumeration of device features")
  public void enumerate_features() {
    Depths enumerate = new Depths();
    enumerate.features = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Summary("Check enumeration of network families")
  @Feature(bucket = ENUMERATION_FAMILIES, stage = ALPHA)
  public void enumerate_families() {
    Depths enumerate = new Depths();
    enumerate.families = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = ALPHA)
  @Summary("Check enumeration of multiple categories")
  public void enumerate_multi() {
    Depths enumerate = new Depths();
    enumerate.families = enumerateIfBucketEnabled(ENUMERATION_FAMILIES);
    enumerate.features = enumerateIfBucketEnabled(ENUMERATION_FEATURES);
    enumerate.refs = enumerateIfBucketEnabled(ENUMERATION_POINTSET);
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  private Depth enumerateIfBucketEnabled(Bucket bucket) {
    return enumerationDepthIf(isBucketEnabled(bucket) ? PLEASE_ENUMERATE : NO_ENUMERATION);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check that a scan scheduled in the past never starts")
  public void scan_single_past() {
    scanAndVerify(LONG_TIME_AGO, NO_SCAN);
  }

  @Test
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled in the recent past")
  public void scan_single_now() {
    scanAndVerify(cleanInstantDate(Instant.now().minus(SCAN_START_DELAY)), NO_ENUMERATION);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled soon")
  public void scan_single_future() {
    scanAndVerify(cleanInstantDate(Instant.now().plus(SCAN_START_DELAY)), NO_ENUMERATION);
  }

  private void scanAndVerify(Date scanStart, ScanMode shouldEnumerate) {
    final boolean checkPending = scanStart.after(new Date());

    initializeDiscovery();
    checkState(scanGeneration == null, "scanStartTime not null");

    scanGeneration = scanStart.toInstant();

    configureScan(scanGeneration, null, shouldEnumerate);

    if (shouldEnumerate == NO_SCAN) {
      waitFor("scan schedule initially not active", this::detailScanComplete);
      sleepFor("false start check delay", SCAN_START_DELAY);
      waitFor("scan schedule still not active", this::detailScanComplete);
      List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
      checkThat("there were no received discovery events", receivedEvents.isEmpty());
      return;
    }

    Duration waitingPeriod = SCAN_START_DELAY.plus(SCAN_START_DELAY);
    Instant sequenceStarted = getNowInstant();

    if (checkPending) {
      waitFor("scheduled scan pending", waitingPeriod, this::detailScanPending);
    }

    waitFor("scheduled scan active", waitingPeriod, this::detailScanActive);

    Instant deviceStateTime = deviceState.timestamp.toInstant();
    Instant startBase = sequenceStarted.isAfter(scanGeneration) ? sequenceStarted : scanGeneration;
    long delta = Math.abs(Duration.between(deviceStateTime, startBase).toSeconds());
    checkThat("scan start near expected generation time", delta <= SCAN_START_JITTER_SEC,
        format("scan start %ss different from expected %s", delta, isoConvert(scanGeneration)));

    waitFor("scheduled scan complete", waitingPeriod, this::detailScanComplete);

    List<DiscoveryEvents> events = popReceivedEvents(DiscoveryEvents.class);

    Date generation = deviceConfig.discovery.families.get(scanFamily).generation;
    Function<DiscoveryEvents, String> invalidator = event -> invalidReasons(event, generation);
    checkThat("discovery events were received", !events.isEmpty());
    Set<String> reasons = events.stream().map(invalidator).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    checkThat("discovery events were valid", reasons.isEmpty(), CSV_JOINER.join(reasons));

    Set<String> duplicates = events.stream().map(x -> x.scan_addr)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet().stream().filter(p -> p.getValue() > 1).map(Entry::getKey)
        .collect(Collectors.toSet());
    checkThat("all scan addresses are unique", duplicates.isEmpty(),
        "duplicates: " + CSV_JOINER.join(duplicates));

    Set<String> discoveredAddresses = events.stream().map(x -> x.scan_addr)
        .collect(Collectors.toSet());
    Set<String> expectedAddresses = siteModel.metadataStream()
        .map(e -> catchToNull(() -> e.getValue().localnet.families.get(scanFamily).addr))
        .filter(Objects::nonNull).collect(Collectors.toSet());
    SetView<String> differences = symmetricDifference(discoveredAddresses, expectedAddresses);
    checkThat("all expected addresses were found", differences.isEmpty(),
        CSV_JOINER.join(differences));
    checkEnumeration(events, shouldEnumerate);
  }

  private String detailScanPending() {
    return ifNotTrueGet(scanPending(Date.from(scanGeneration)).test(scanFamily),
        format("Expected pending %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private String detailScanActive() {
    return ifNotTrueGet(scanActive(Date.from(scanGeneration)).test(scanFamily),
        format("Expected active %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private String detailScanComplete() {
    return ifNotTrueGet(scanComplete(Date.from(scanGeneration)).test(scanFamily),
        format("Expected complete %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private String invalidReasons(DiscoveryEvents discoveryEvent, Date scanGeneration) {
    try {
      assertEquals("bad scan family", scanFamily, discoveryEvent.scan_family);
      assertEquals("bad generation", scanGeneration, discoveryEvent.generation);
      assertNotNull("empty scan address", discoveryEvent.scan_addr);
    } catch (Exception e) {
      return e.getMessage();
    }
    return null;
  }

  private String describedFamilyState() {
    return stringifyTerse(getFamilyDiscoveryState());
  }

  private FamilyDiscoveryState getFamilyDiscoveryState() {
    return ifNotNullGet(deviceState.discovery.families, map -> map.get(scanFamily));
  }

  private void checkEnumeration(List<DiscoveryEvents> receivedEvents, ScanMode shouldEnumerate) {
    Predicate<DiscoveryEvents> hasRefs = event -> event.refs != null && !event.refs.isEmpty();
    if (shouldEnumerate == PLEASE_ENUMERATE) {
      checkThat("all events have discovered refs", receivedEvents.stream().allMatch(hasRefs));
    } else {
      checkThat("no events have discovered refs", receivedEvents.stream().noneMatch(hasRefs));
    }
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check periodic scan on a fixed schedule amd enumeration")
  public void scan_periodic_now_enumerate() {
    initializeDiscovery();
    checkState(scanGeneration == null, "scanStartTime not null");
    scanGeneration = cleanDate().toInstant();
    configureScan(scanGeneration, SCAN_START_DELAY, PLEASE_ENUMERATE);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY.getSeconds() * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    String oneFamily = metaFamilies.iterator().next();
    Instant finishTime = deviceState.discovery.families.get(oneFamily).generation.toInstant();
    checkThat("scan did not terminate prematurely",
        metaFamilies.stream().noneMatch(scanComplete(Date.from(finishTime))));
    List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
    checkEnumeration(receivedEvents, PLEASE_ENUMERATE);
  }

  private void initializeDiscovery() {
    metaFamilies = catchToNull(() -> deviceMetadata.discovery.families.keySet());
    if (metaFamilies == null || metaFamilies.isEmpty()) {
      skipTest("No discovery families configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    untilTrue("discovery families defined", () -> deviceState.discovery.families != null);
    HashMap<String, FamilyDiscoveryConfig> configFamilies = deviceConfig.discovery.families;
    HashMap<String, FamilyDiscoveryState> stateFamilies = deviceState.discovery.families;
    waitFor("discovery family keys match", () -> joinOrNull("mismatch: ",
        symmetricDifference(configFamilies.keySet(), stateFamilies.keySet())
    ));
    untilTrue("no scans active",
        () -> stateFamilies.keySet().stream().noneMatch(scanActive()));
  }

  private void configureScan(Instant startTime, Duration scanInterval, ScanMode shouldEnumerate) {
    Integer intervalSec = ofNullable(scanInterval).map(Duration::getSeconds).map(Long::intValue)
        .orElse(null);
    info(format("%s configured for family %s starting at %s evey %ss",
        shouldEnumerate == PLEASE_ENUMERATE ? "Enumeration" : "Scan", scanFamily,
        isoConvert(startTime), intervalSec));
    FamilyDiscoveryConfig configFamily = getConfigFamily(scanFamily);
    configFamily.generation = SemanticDate.describe("family generation", startTime);
    configFamily.depth = enumerationDepthIf(shouldEnumerate);
    configFamily.scan_interval_sec = intervalSec;
    configFamily.scan_duration_sec = ofNullable(intervalSec).orElse(SCAN_START_DELAY_SEC);
    popReceivedEvents(DiscoveryEvents.class);
  }

  private FamilyDiscoveryConfig getConfigFamily(String family) {
    return deviceConfig.discovery.families.computeIfAbsent(family,
        adding -> new FamilyDiscoveryConfig());
  }

  private FamilyDiscoveryState getStateFamily(String family) {
    return deviceState.discovery.families.get(family);
  }

  private Predicate<String> scanPending(Date startTime) {
    return family -> {
      FamilyDiscoveryState stateFamily = getStateFamily(family);
      return stateFamily != null
          && dateEquals(stateFamily.generation, startTime)
          && stateFamily.phase == PENDING
          && deviceState.timestamp.before(startTime);
    };
  }

  private Predicate<String> scanActive() {
    return family -> getStateFamily(family).phase == ACTIVE;
  }

  private Predicate<String> scanActive(Date startTime) {
    return family -> dateEquals(getStateFamily(family).generation, startTime)
        && getStateFamily(family).phase == ACTIVE;
  }

  private Predicate<String> scanComplete(Date startTime) {
    return family -> {
      FamilyDiscoveryState stateFamily = getStateFamily(family);
      return stateFamily != null
          && dateEquals(stateFamily.generation, startTime)
          && stateFamily.phase == STOPPED
          && deviceState.timestamp.after(startTime);
    };
  }

  /**
   * Basic enumeration to capture some of the kinds of scans to be tested.
   */
  enum ScanMode {
    NO_SCAN, NO_ENUMERATION, PLEASE_ENUMERATE
  }
}
