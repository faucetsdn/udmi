package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.NINETY_SECONDS_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.cleanDate;
import static com.google.udmi.util.CleanDateFormat.cleanInstantDate;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Bucket.DISCOVERY_SCAN;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
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
import com.google.udmi.util.CleanDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Enumerate;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequenceBase {

  public static final int SCAN_START_DELAY_SEC = 10;
  private static final int SCAN_ITERATIONS = 2;
  private static final ProtocolFamily scanFamily = ProtocolFamily.VENDOR;
  private HashMap<ProtocolFamily, Date> previousGenerations;
  private Set<ProtocolFamily> families;

  private static boolean isActive(Entry<String, FeatureDiscovery> entry) {
    return Optional.ofNullable(entry.getValue().stage).orElse(STABLE).compareTo(BETA) >= 0;
  }

  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("discovery");
  }

  private DiscoveryEvent runEnumeration(Enumerate enumerate) {
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumerate = enumerate;
    untilTrue("enumeration not active", () -> deviceState.discovery.generation == null);

    Date startTime = SemanticDate.describe("generation start time", cleanDate());
    deviceConfig.discovery.generation = startTime;
    info("Starting empty enumeration at " + isoConvert(startTime));
    untilTrue("matching enumeration generation",
        () -> deviceState.discovery.generation.equals(startTime));

    deviceConfig.discovery.generation = null;
    untilTrue("cleared enumeration generation", () -> deviceState.discovery.generation == null);

    List<DiscoveryEvent> allEvents = popReceivedEvents(DiscoveryEvent.class);
    // Filter for enumeration events, since there will sometimes be lingering scan events.
    List<DiscoveryEvent> enumEvents = allEvents.stream().filter(event -> event.scan_addr == null)
        .collect(Collectors.toList());
    assertEquals("a single discovery event received", 1, enumEvents.size());
    DiscoveryEvent event = enumEvents.get(0);
    info("Received discovery generation " + isoConvert(event.generation));
    assertEquals("matching event generation", startTime, event.generation);
    return event;
  }

  private void checkSelfEnumeration(DiscoveryEvent event, Enumerate enumerate) {
    if (isTrue(enumerate.families)) {
      Set<ProtocolFamily> models = Optional.ofNullable(deviceMetadata.localnet)
          .map(localnet -> localnet.families.keySet()).orElse(null);
      Set<ProtocolFamily> events = Optional.ofNullable(event.families).map(Map::keySet)
          .orElse(null);
      checkThat("family enumeration matches", () -> models.size() == events.size());
    } else {
      checkThat("no family enumeration", () -> event.families == null);
    }

    if (isTrue(enumerate.features)) {
      checkFeatureDiscovery(event.features);
    } else {
      checkThat("no feature enumeration", () -> event.features == null);
    }

    if (isTrue(enumerate.points)) {
      int expectedSize = Optional.ofNullable(deviceMetadata.pointset.points).map(HashMap::size)
          .orElse(0);
      checkThat("enumerated point count matches", () -> event.points.size() == expectedSize);
    } else {
      checkThat("no point enumeration", () -> event.points == null);
    }
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
    return Optional.ofNullable(condition).orElse(false);
  }

  @Test(timeout = NINETY_SECONDS_MS)
  @Feature(bucket = ENUMERATION, stage = PREVIEW)
  @Summary("Check enumeration of nothing at all")
  public void empty_enumeration() {
    Enumerate enumerate = new Enumerate();
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = NINETY_SECONDS_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = ALPHA)
  @Summary("Check enumeration of device points")
  public void pointset_enumeration() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      skipTest("No metadata pointset points defined");
    }
    Enumerate enumerate = new Enumerate();
    enumerate.points = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = NINETY_SECONDS_MS)
  @Feature(bucket = ENUMERATION_FEATURES, stage = PREVIEW)
  @Summary("Check enumeration of device features")
  public void feature_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.features = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Summary("Check enumeration of network families")
  @Feature(bucket = ENUMERATION_FAMILIES, stage = ALPHA)
  public void family_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.families = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = ALPHA)
  @Summary("Check enumeration of multiple categories")
  public void multi_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.families = isBucketEnabled(ENUMERATION_FAMILIES);
    enumerate.features = isBucketEnabled(ENUMERATION_FEATURES);
    enumerate.points = isBucketEnabled(ENUMERATION_POINTSET);
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled right now")
  public void single_scan_past() {
    ifTrueSkipTest(true, "Not yet implemented");
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled immediately")
  public void single_scan_future() {
    initializeDiscovery();
    Date startTime = cleanInstantDate(Instant.now().plusSeconds(SCAN_START_DELAY_SEC));
    boolean shouldEnumerate = false;
    configureScan(startTime, null, shouldEnumerate);
    untilReady("scheduled scan start",
        () -> {
          FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState();
          return ifNotTrueGet(familyDiscoveryState.active,
              () -> format("Waiting for %s scan start: %s ", scanFamily,
                  stringifyTerse(familyDiscoveryState)));
        });
    assertFalse("scan started before activation: " + stringifyTerse(getFamilyDiscoveryState()),
        deviceState.timestamp.before(startTime));
    untilReady("scheduled scan stop",
        () -> {
          FamilyDiscoveryState familyDiscoveryState = getFamilyDiscoveryState();
          return ifTrueGet(familyDiscoveryState.active,
              () -> format("Waiting for %s scan stop: %s ", scanFamily,
                  stringifyTerse(familyDiscoveryState)));
        });
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(DiscoveryEvent.class);
    assertFalse("received discovery events", receivedEvents.isEmpty());
    checkEnumeration(receivedEvents, shouldEnumerate);
  }

  private FamilyDiscoveryState getFamilyDiscoveryState() {
    return deviceState.discovery.families.get(
        scanFamily);
  }

  private void checkEnumeration(List<DiscoveryEvent> receivedEvents, boolean shouldEnumerate) {
    Predicate<DiscoveryEvent> hasPoints = event -> event.points != null
        && !event.points.isEmpty();
    if (shouldEnumerate) {
      assertTrue("with enumeration", receivedEvents.stream().allMatch(hasPoints));
    } else {
      assertTrue("sans enumeration", receivedEvents.stream().noneMatch(hasPoints));
    }
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check periodic scan of address families")
  public void periodic_scan_fixed() {
    initializeDiscovery();
    Date startTime = cleanDate();
    boolean shouldEnumerate = true;
    configureScan(startTime, SCAN_START_DELAY_SEC, shouldEnumerate);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY_SEC * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    ProtocolFamily oneFamily = families.iterator().next();
    Date finishTime = deviceState.discovery.families.get(oneFamily).generation;
    assertTrue("premature termination",
        families.stream().noneMatch(familyScanComplete(finishTime)));
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    Set<ProtocolFamily> eventFamilies = receivedEvents.stream()
        .flatMap(event -> event.families.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested families present", eventFamilies.containsAll(families));
    Map<String, List<DiscoveryEvent>> receivedEventsGrouped = receivedEvents.stream()
        .collect(Collectors.groupingBy(e -> e.scan_family + "." + e.scan_addr));
    assertTrue("scan iteration",
        receivedEventsGrouped.values().stream().allMatch(list -> list.size() == SCAN_ITERATIONS));
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check periodic scan of address families")
  public void periodic_scan_floating() {
    ifTrueSkipTest(true, "Not yet implemented");
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  @Summary("Check results of a single scan scheduled right now")
  public void cancel_before_start() {
    ifTrueSkipTest(true, "Not yet implemented");
  }

  private void initializeDiscovery() {
    families = catchToNull(() -> deviceMetadata.discovery.families.keySet());
    if (families == null || families.isEmpty()) {
      skipTest("No discovery families configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    untilTrue("all scans not active", () -> families.stream().noneMatch(familyScanActivated(null)));
    previousGenerations = new HashMap<>();
    families.forEach(family -> previousGenerations.put(family, getStateFamilyGeneration(family)));
  }

  private void configureScan(Date startTime, Integer scanIntervalSec, Boolean enumerate) {
    info(format("%s configured for family %s starting at %s evey %ss",
        isTrue(enumerate) ? "Enumeration" : "Scan", scanFamily, startTime, scanIntervalSec));
    FamilyDiscoveryConfig configFamily = getConfigFamily(scanFamily);
    configFamily.generation = SemanticDate.describe("family generation", startTime);
    configFamily.enumerate = enumerate;
    configFamily.scan_interval_sec = scanIntervalSec;
    popReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
  }

  private FamilyDiscoveryConfig getConfigFamily(ProtocolFamily family) {
    return deviceConfig.discovery.families.computeIfAbsent(family,
        adding -> new FamilyDiscoveryConfig());
  }

  private Date getStateFamilyGeneration(ProtocolFamily family) {
    return catchToNull(() -> getStateFamily(family).generation);
  }

  private boolean stateGenerationMismatch(ProtocolFamily family) {
    return !Objects.equals(previousGenerations.get(family), getStateFamilyGeneration(family));
  }

  private FamilyDiscoveryState getStateFamily(ProtocolFamily family) {
    return deviceState.discovery.families.get(family);
  }

  private Predicate<ProtocolFamily> familyScanActive(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        && CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<ProtocolFamily> familyScanActivated(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        || CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<? super ProtocolFamily> familyScanComplete(Date startTime) {
    return familyScanActivated(startTime).and(familyScanActive(startTime).negate());
  }
}
