package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.symmetricDifference;
import static com.google.daq.mqtt.sequencer.DiscoveryScanMode.NO_ENUMERATION;
import static com.google.daq.mqtt.sequencer.DiscoveryScanMode.NO_SCAN;
import static com.google.daq.mqtt.sequencer.DiscoveryScanMode.PLEASE_ENUMERATE;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.cleanInstantDate;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotEmptyThrow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNullElse;
import static com.google.udmi.util.GeneralUtils.joinOrNull;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static udmi.schema.Bucket.DISCOVERY_SCAN;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
import static udmi.schema.Enumerations.Depth.ENTRIES;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;
import static udmi.schema.FeatureDiscovery.FeatureStage.BETA;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;
import static udmi.schema.FeatureDiscovery.FeatureStage.STABLE;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.sequencer.DiscoveryScanMode;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.util.providers.FamilyProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import udmi.schema.Bucket;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequenceBase {

  public static final Duration SCAN_START_DELAY = Duration.ofSeconds(20);
  public static final Duration WAITING_PERIOD = SCAN_START_DELAY.plus(SCAN_START_DELAY);
  public static final int SCAN_START_JITTER_SEC = 10;
  private static final int SCAN_ITERATIONS = 2;
  private static final long RANDOM_YEAR_SEC = (long) (Math.random() * 60 * 60 * 24 * 365);
  private static final Instant BASE_OLD_TIME = Instant.parse("2020-10-18T12:02:01Z");
  private static final Date LONG_TIME_AGO = Date.from(BASE_OLD_TIME.plusSeconds(RANDOM_YEAR_SEC));
  private static final int SCAN_DURATION_SEC = 10;
  private static final String DISCOVERY_TARGET = "scan_family";
  private Set<String> metaFamilies;
  private Instant scanGeneration;
  private String scanFamily;
  private FamilyProvider providerFamily;

  private static boolean isActive(Entry<String, FeatureDiscovery> entry) {
    return ofNullable(entry.getValue().stage).orElse(STABLE).compareTo(BETA) >= 0;
  }

  private static Depth enumerationDepthIf(DiscoveryScanMode shouldEnumerate) {
    return shouldEnumerate == PLEASE_ENUMERATE ? ENTRIES : null;
  }

  /**
   * Do some basic setup for things applicable to all tests.
   */
  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("discovery");
    scanFamily = catchToNull(() ->
        (String) deviceMetadata.testing.targets.get(DISCOVERY_TARGET).target_value);
    providerFamily = FamilyProvider.NAMED_FAMILIES.get(scanFamily);
    checkState(providerFamily != null, "No provider family found for scan family " + scanFamily);
  }

  private DiscoveryEvents runEnumeration(Enumerations depths) {
    deviceConfig.discovery = ofNullable(deviceConfig.discovery).orElseGet(DiscoveryConfig::new);
    deviceConfig.discovery.enumerations = depths;
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
    List<DiscoveryEvents> enumEvents = allEvents.stream().filter(event -> event.addr == null)
        .toList();
    assertEquals("a single discovery event received", 1, enumEvents.size());
    DiscoveryEvents event = enumEvents.get(0);
    info("Received discovery generation " + isoConvert(event.generation));
    assertEquals("matching event generation", startTime, event.generation);
    return event;
  }

  private void checkSelfEnumeration(DiscoveryEvents event, Enumerations depths) {
    if (shouldEnumerate(depths.families)) {
      Set<String> models = ofNullable(deviceMetadata.localnet)
          .map(localnet -> localnet.families.keySet()).orElse(ImmutableSet.of());
      Set<String> events = ofNullable(event.families).map(Map::keySet)
          .orElse(ImmutableSet.of());

      String detail = models.size() == events.size() ? null
          : format("received %s, expected %s", CSV_JOINER.join(events), CSV_JOINER.join(models));
      checkThat("family enumeration size matches", detail);
    } else {
      checkThat("no family enumeration exists", event.families == null);
    }

    if (shouldEnumerate(depths.features)) {
      checkFeatureDiscovery(event.features);
    } else {
      checkThat("no feature enumeration exists", event.features == null);
    }

    if (shouldEnumerate(depths.points)) {
      checkThat("enumerated point count matches",
          enumeratedPoints(event, deviceMetadata.pointset.points));
    } else {
      checkThat("no point enumeration exists", event.refs == null);
    }
  }

  private String enumeratedPoints(DiscoveryEvents discovery,
      HashMap<String, PointPointsetModel> points) {
    Set<String> discoveryPoints = discovery.points.keySet();
    Set<String> metadataPoints = points.keySet();
    return discoveryPoints.equals(metadataPoints) ? null : format(
        "Discovered points %s don't match metadata points %s", discoveryPoints, metadataPoints);
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
    checkFeatureMetadata(enumeratedFeatures);
    Set<String> unofficial = enumeratedFeatures.stream()
        .filter(feature -> !Bucket.contains(feature)).collect(Collectors.toSet());
    String format = format("unrecognized { %s }", CSV_JOINER.join(unofficial));
    checkThat("all enumerated features are official buckets", unofficial::isEmpty, format);
  }

  private void checkFeatureMetadata(Set<String> enumeratedFeatures) {
    ifNullSkipTest(deviceMetadata.features, "No metadata.features defined");
    Set<String> enabledFeatures = deviceMetadata.features.entrySet().stream()
        .filter(DiscoverySequences::isActive).map(Entry::getKey).collect(Collectors.toSet());
    SetView<String> extraFeatures = Sets.difference(enumeratedFeatures, enabledFeatures);
    SetView<String> missingFeatures = Sets.difference(enabledFeatures, enumeratedFeatures);
    SetView<String> difference = Sets.union(extraFeatures, missingFeatures);
    String details = format("missing { %s }, extra { %s }", CSV_JOINER.join(missingFeatures),
        CSV_JOINER.join(extraFeatures));
    checkThat("feature enumeration matches metadata", difference::isEmpty, details);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION, stage = PREVIEW)
  @Summary("Check enumeration of nothing at all")
  public void enumerate_nothing() {
    Enumerations enumerate = new Enumerations();
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = PREVIEW)
  @Summary("Check enumeration of device points")
  public void enumerate_pointset() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      skipTest("No metadata pointset points defined");
    }
    Enumerations enumerate = new Enumerations();
    enumerate.points = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_FEATURES, stage = PREVIEW)
  @Summary("Check enumeration of device features")
  public void enumerate_features() {
    Enumerations enumerate = new Enumerations();
    enumerate.features = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Summary("Check enumeration of network families")
  @Feature(bucket = ENUMERATION_FAMILIES, stage = PREVIEW)
  public void enumerate_families() {
    Enumerations enumerate = new Enumerations();
    enumerate.families = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = PREVIEW)
  @Summary("Check enumeration of multiple categories")
  public void enumerate_multi() {
    Enumerations enumerate = new Enumerations();
    enumerate.families = enumerateIfBucketEnabled(ENUMERATION_FAMILIES);
    enumerate.features = enumerateIfBucketEnabled(ENUMERATION_FEATURES);
    enumerate.points = enumerateIfBucketEnabled(ENUMERATION_POINTSET);
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  private Depth enumerateIfBucketEnabled(Bucket bucket) {
    return enumerationDepthIf(isBucketEnabled(bucket) ? PLEASE_ENUMERATE : NO_ENUMERATION);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = PREVIEW)
  @Summary("Check that a scan scheduled in the past never starts")
  public void scan_single_past() {
    scanAndVerify(LONG_TIME_AGO, NO_SCAN);
  }

  @Test
  @Feature(bucket = DISCOVERY_SCAN, stage = PREVIEW)
  @Summary("Check results of a single scan scheduled in the recent past")
  public void scan_single_now() {
    scanAndVerify(cleanInstantDate(Instant.now().minusSeconds(1)), NO_ENUMERATION);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = PREVIEW)
  @Summary("Check results of a single scan scheduled soon")
  public void scan_single_future() {
    scanAndVerify(cleanInstantDate(Instant.now().plus(SCAN_START_DELAY)), NO_ENUMERATION);
  }

  private void scanAndVerify(Date scanStart, DiscoveryScanMode shouldEnumerate) {
    final boolean scheduledStart = scanStart.after(new Date());

    initializeDiscovery();
    checkState(scanGeneration == null, "scanStartTime not null");

    scanGeneration = scanStart.toInstant();

    configureScan(scanGeneration, null, shouldEnumerate);
    Instant scanConfigured = getNowInstant();

    if (shouldEnumerate == NO_SCAN) {
      waitUntil("scan schedule initially not active", this::detailScanStopped);
      sleepFor("false start check delay", SCAN_START_DELAY);
      waitUntil("scan schedule still not active", this::detailScanStopped);
      List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
      checkThat("there were no received discovery events", receivedEvents.isEmpty());
      return;
    }

    if (scheduledStart) {
      waitUntil("scheduled scan pending", WAITING_PERIOD, this::detailScanPending);
    }

    Instant expectedStart = scheduledStart ? scanGeneration : scanConfigured;
    waitUntil("scheduled scan active", WAITING_PERIOD, this::detailScanActive);
    Instant scanStarted = getNowInstant();
    long deltaStart = Math.abs(Duration.between(scanStarted, expectedStart).toSeconds());
    checkThat("scan started at time", deltaStart <= SCAN_START_JITTER_SEC,
        format("scan start %s %ss different from expected %s", isoConvert(scanStarted), deltaStart,
            isoConvert(expectedStart)));

    Instant expectedFinish = scanStarted.plusSeconds(SCAN_DURATION_SEC);
    waitUntil("scheduled scan complete", WAITING_PERIOD, this::detailScanStopped);
    Instant scanFinished = getNowInstant();
    long deltaFinish = Math.abs(Duration.between(scanFinished, expectedFinish).toSeconds());
    checkThat("scan completed at time", deltaFinish <= SCAN_START_JITTER_SEC,
        format("scan completed %ss different from expected %s", deltaFinish,
            isoConvert(expectedFinish)));

    Integer stateEvents = deviceState.discovery.families.get(scanFamily).active_count;
    List<DiscoveryEvents> events = popReceivedEvents(DiscoveryEvents.class);
    Date generation = deviceConfig.discovery.families.get(scanFamily).generation;
    Function<DiscoveryEvents, List<String>> invalidator = event ->
        invalidReasons(event, generation);
    checkThat("discovery events were received", events.size() == stateEvents);
    List<String> reasons = events.stream().map(invalidator).flatMap(List::stream)
        .collect(Collectors.toList());
    reasons.addAll(checkEnumeration(events, shouldEnumerate));

    checkThat("discovery events were valid", reasons.isEmpty(), CSV_JOINER.join(reasons));

    Set<String> duplicates = events.stream().map(x -> x.addr)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet().stream().filter(p -> p.getValue() > 1).map(Entry::getKey)
        .collect(Collectors.toSet());
    checkThat("all scan addresses are unique", duplicates.isEmpty(),
        "duplicates: " + CSV_JOINER.join(duplicates));

    Set<String> discoveredAddresses = events.stream().map(x -> x.addr)
        .collect(Collectors.toSet());
    Set<String> expectedAddresses = siteModel.metadataStream().map(this::scanFamilyAddr)
        .filter(Objects::nonNull).collect(Collectors.toSet());
    SetView<String> differences = symmetricDifference(discoveredAddresses, expectedAddresses);
    checkThat("all expected addresses were found", differences.isEmpty(),
        format("expected %s, found %s", expectedAddresses, discoveredAddresses));

  }

  private String detailScanPending() {
    return ifNotTrueGet(scanPending(Date.from(scanGeneration)).test(scanFamily),
        format("Expected pending %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private String detailScanActive() {
    return ifNotTrueGet(scanActive(Date.from(scanGeneration)).test(scanFamily),
        format("Expected active %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private String detailScanStopped() {
    return ifNotTrueGet(scanStopped(Date.from(scanGeneration)).test(scanFamily),
        format("Expected stopped %s but %s", isoConvert(scanGeneration), describedFamilyState()));
  }

  private List<String> invalidReasons(DiscoveryEvents discoveryEvent, Date scanGeneration) {
    List<String> exceptions = new ArrayList<>();
    addIfCaught(exceptions,
        () -> assertEquals("bad scan family", scanFamily, discoveryEvent.family));
    addIfCaught(exceptions,
        () -> assertEquals("bad generation", scanGeneration, discoveryEvent.generation));
    addIfCaught(exceptions, () -> assertNotNull("empty scan address", discoveryEvent.addr));
    addIfCaught(exceptions, () -> providerFamily.validateAddr(discoveryEvent.addr));
    return exceptions;
  }

  private void addIfCaught(List<String> exceptions, Runnable checker) {
    try {
      checker.run();
    } catch (Exception e) {
      exceptions.add(e.getMessage());
    }
  }

  private String describedFamilyState() {
    return stringifyTerse(getFamilyDiscoveryState());
  }

  private FamilyDiscoveryState getFamilyDiscoveryState() {
    return ifNotNullGet(deviceState.discovery.families, map -> map.get(scanFamily));
  }

  private List<String> checkEnumeration(List<DiscoveryEvents> receivedEvents,
      DiscoveryScanMode shouldEnumerate) {
    List<String> exceptions = new ArrayList<>();
    if (shouldEnumerate == PLEASE_ENUMERATE) {
      addIfCaught(exceptions, () ->
          checkThat("all events have matching refs", mismatchedDetail(receivedEvents)));
    } else {
      addIfCaught(exceptions, () -> checkThat("no events have discovered refs",
          receivedEvents.stream().noneMatch(event -> nonNull(event.refs))));
    }
    return exceptions;
  }

  private String mismatchedDetail(List<DiscoveryEvents> receivedEvents) {
    List<String> strings = receivedEvents.stream()
        .map(this::refsMatch).filter(Objects::nonNull).toList();
    return strings.isEmpty() ? null : strings.toString();
  }

  private String refsMatch(DiscoveryEvents discoveryEvents) {
    Entry<String, Metadata> deviceEntry = targetMetadata(discoveryEvents.addr);
    HashMap<String, PointPointsetModel> devicePoints = deviceEntry.getValue().pointset.points;
    Set<String> metadataRefs = devicePoints.values().stream()
        .map(x -> x.ref).filter(Objects::nonNull).collect(Collectors.toSet());
    Set<String> discoveredRefs = discoveryEvents.refs.keySet();
    SetView<String> extraMetadata = Sets.difference(metadataRefs, discoveredRefs);
    SetView<String> extraDiscovered = Sets.difference(discoveredRefs, metadataRefs);
    return extraMetadata.isEmpty() && extraDiscovered.isEmpty() ? null
        : format("Device %s has extra metadata refs %s and/or extra discovered refs %s",
            deviceEntry.getKey(), extraMetadata, extraDiscovered);
  }

  private Entry<String, Metadata> targetMetadata(String scanAddr) {
    return siteModel.allMetadata().entrySet().stream()
        .filter(entry -> scanAddr.equals(scanFamilyAddr(entry)))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            format("No device match found for %s addr %s", scanFamily, scanAddr)));
  }

  private String scanFamilyAddr(Entry<String, Metadata> entry) {
    return catchToNull(() -> entry.getValue().localnet.families.get(scanFamily).addr);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = PREVIEW)
  @Summary("Check periodic scan on a fixed schedule and enumeration")
  public void scan_periodic_now_enumerate() {
    initializeDiscovery();
    checkState(scanGeneration == null, "scanStartTime not null");
    scanGeneration = cleanDate().toInstant();
    configureScan(scanGeneration, SCAN_START_DELAY, PLEASE_ENUMERATE);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY.getSeconds() * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    Instant finishTime = deviceState.discovery.families.get(scanFamily).generation.toInstant();
    checkThat("scan did not terminate prematurely",
        metaFamilies.stream().noneMatch(scanStopped(Date.from(finishTime))));
    List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
    quietlyCheckThat("discovery events were received", !receivedEvents.isEmpty());
    Date generation = receivedEvents.get(receivedEvents.size() - 1).generation;
    List<DiscoveryEvents> lastGenerationEvents = receivedEvents.stream()
        .filter(event -> event.generation.equals(generation)).toList();
    ifNotEmptyThrow(checkEnumeration(lastGenerationEvents, PLEASE_ENUMERATE), CSV_JOINER::join);
  }

  private void initializeDiscovery() {
    ifNullSkipTest(scanFamily,
        format("metadata.testing.targets.%s.target_value not defined", DISCOVERY_TARGET));
    metaFamilies = catchToNull(() -> deviceMetadata.discovery.families.keySet());
    if (metaFamilies == null || metaFamilies.isEmpty()) {
      skipTest("No discovery families configured");
    }
    checkState(metaFamilies.contains(scanFamily),
        format("Discovery scan family %s not specified in metadata", scanFamily));
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    untilTrue("discovery families defined", () -> deviceState.discovery.families != null);
    Map<String, FamilyDiscoveryConfig> configFamilies = deviceConfig.discovery.families;
    Map<String, FamilyDiscoveryState> stateFamilies = deviceState.discovery.families;
    waitUntil("discovery family keys match", () -> joinOrNull("mismatch: ",
        symmetricDifference(configFamilies.keySet(), stateFamilies.keySet())
    ));
    untilTrue("no scans active",
        () -> stateFamilies.keySet().stream().noneMatch(scanActive()));
  }

  private void configureScan(Instant startTime, Duration scanInterval,
      DiscoveryScanMode shouldEnumerate) {
    Integer intervalSec = ofNullable(scanInterval).map(Duration::getSeconds).map(Long::intValue)
        .orElse(null);
    info(format("%s configured for family %s starting at %s evey %ss",
        shouldEnumerate == PLEASE_ENUMERATE ? "Enumeration" : "Scan", scanFamily,
        isoConvert(startTime), intervalSec));
    FamilyDiscoveryConfig configFamily = getConfigFamily(scanFamily);
    configFamily.generation = SemanticDate.describe("family generation", startTime);
    configFamily.depth = enumerationDepthIf(shouldEnumerate);
    configFamily.scan_interval_sec = intervalSec;
    configFamily.scan_duration_sec = ofNullable(intervalSec).orElse(SCAN_DURATION_SEC);
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

  private Predicate<String> scanStopped(Date startTime) {
    return family -> {
      FamilyDiscoveryState stateFamily = getStateFamily(family);
      return stateFamily != null
          && dateEquals(stateFamily.generation, startTime)
          && stateFamily.phase == STOPPED
          && deviceState.timestamp.after(startTime);
    };
  }
}
