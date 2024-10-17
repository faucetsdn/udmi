package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.symmetricDifference;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.cleanInstantDate;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifNullElse;
import static com.google.udmi.util.GeneralUtils.joinOrNull;
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
import daq.pubber.ProtocolFamily;
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
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
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
  private static final Date LONG_TIME_AGO = new Date(12897321);
  private Set<String> metaFamilies;
  private Date scanStartTime;

  private static boolean isActive(Entry<String, FeatureDiscovery> entry) {
    return ofNullable(entry.getValue().stage).orElse(STABLE).compareTo(BETA) >= 0;
  }

  @Nullable
  private static Depth enumerationDepthIf(boolean shouldEnumerate) {
    return shouldEnumerate ? ENTRIES : null;
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
  public void empty_enumeration() {
    Depths enumerate = new Depths();
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = ALPHA)
  @Summary("Check enumeration of device points")
  public void pointset_enumeration() {
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
  public void feature_enumeration() {
    Depths enumerate = new Depths();
    enumerate.features = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Summary("Check enumeration of network families")
  @Feature(bucket = ENUMERATION_FAMILIES, stage = ALPHA)
  public void family_enumeration() {
    Depths enumerate = new Depths();
    enumerate.families = ENTRIES;
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = ALPHA)
  @Summary("Check enumeration of multiple categories")
  public void multi_enumeration() {
    Depths enumerate = new Depths();
    enumerate.families = enumerateIfBucketEnabled(ENUMERATION_FAMILIES);
    enumerate.features = enumerateIfBucketEnabled(ENUMERATION_FEATURES);
    enumerate.refs = enumerateIfBucketEnabled(ENUMERATION_POINTSET);
    DiscoveryEvents event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  private Depth enumerateIfBucketEnabled(Bucket bucket) {
    return enumerationDepthIf(isBucketEnabled(bucket));
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check that a scan scheduled in the past never starts")
  public void single_scan_past() {
    initializeDiscovery();
    scanStartTime = LONG_TIME_AGO;
    boolean shouldEnumerate = false;
    configureScan(scanStartTime, null, shouldEnumerate);
    waitFor("scan schedule initially complete", this::detailScanComplete);
    sleepFor("false start check delay", SCAN_START_DELAY);
    waitFor("scan schedule still complete", this::detailScanComplete);
    List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
    checkThat("there were no received discovery events", receivedEvents.isEmpty());
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled soon")
  public void single_scan_future() {
    initializeDiscovery();
    checkState(scanStartTime == null, "scanStartTime not null");
    scanStartTime = cleanInstantDate(Instant.now().plus(SCAN_START_DELAY));
    boolean shouldEnumerate = false;
    configureScan(scanStartTime, null, shouldEnumerate);
    Duration waitingPeriod = SCAN_START_DELAY.plus(SCAN_START_DELAY);

    waitFor("scheduled scan pending", waitingPeriod, this::detailScanPending);

    waitFor("scheduled scan active", waitingPeriod, this::detailScanActive);

    long delta = Math.abs(
        Duration.between(deviceState.timestamp.toInstant(), scanStartTime.toInstant()).toSeconds());
    checkThat("scan start near expected generation time", delta <= SCAN_START_JITTER_SEC,
        format("scan start %ss different from expected %s", delta, isoConvert(scanStartTime)));

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
        .entrySet().stream().filter(p -> p.getValue() > 1).map(Map.Entry::getKey)
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
    return ifNotTrueGet(scanPending(scanStartTime).test(scanFamily),
        format("Expected pending %s but %s", scanStartTime, describedFamilyState()));
  }

  private String detailScanActive() {
    return ifNotTrueGet(scanActive(scanStartTime).test(scanFamily),
        format("Expected active %s but %s", scanStartTime, describedFamilyState()));
  }

  private String detailScanComplete() {
    return ifNotTrueGet(scanComplete(scanStartTime).test(scanFamily),
        format("Expected complete %s but %s", isoConvert(scanStartTime), describedFamilyState()));
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

  private void checkEnumeration(List<DiscoveryEvents> receivedEvents, boolean shouldEnumerate) {
    Predicate<DiscoveryEvents> hasRefs = event -> event.refs != null && !event.refs.isEmpty();
    if (shouldEnumerate) {
      checkThat("all events have discovered refs", receivedEvents.stream().allMatch(hasRefs));
    } else {
      checkThat("no events have discovered refs", receivedEvents.stream().noneMatch(hasRefs));
    }
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check periodic scan on a fixed schedule amd enumeration")
  public void periodic_scan_fixed_enumerate() {
    initializeDiscovery();
    checkState(scanStartTime == null, "scanStartTime not null");
    scanStartTime = cleanDate();
    boolean shouldEnumerate = true;
    configureScan(scanStartTime, SCAN_START_DELAY, shouldEnumerate);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY.getSeconds() * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    String oneFamily = metaFamilies.iterator().next();
    Date finishTime = deviceState.discovery.families.get(oneFamily).generation;
    checkThat("scan did not terminate prematurely",
        metaFamilies.stream().noneMatch(scanComplete(finishTime)));
    List<DiscoveryEvents> receivedEvents = popReceivedEvents(DiscoveryEvents.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check periodic scan on a floating schedule")
  public void periodic_scan_floating() {
    ifTrueSkipTest(true, "Not yet implemented");
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of cancelling a pending scan")
  public void cancel_before_start() {
    ifTrueSkipTest(true, "Not yet implemented");
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of cancelling a periodic scan")
  public void cancel_periodic() {
    ifTrueSkipTest(true, "Not yet implemented");
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

  private void configureScan(Date startTime, Duration scanInterval, boolean shouldEnumerate) {
    Integer intervalSec = ofNullable(scanInterval).map(Duration::getSeconds).map(Long::intValue)
        .orElse(null);
    info(format("%s configured for family %s starting at %s evey %ss",
        isTrue(shouldEnumerate) ? "Enumeration" : "Scan", scanFamily, startTime,
        intervalSec));
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

  private Date getStateFamilyGeneration(String family) {
    return catchToNull(() -> getStateFamily(family).generation);
  }

  private FamilyDiscoveryState getStateFamily(String family) {
    return deviceState.discovery.families.get(family);
  }

  private Predicate<String> scanAbsent() {
    return family -> getStateFamily(family) == null;
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
}
