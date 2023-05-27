package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static udmi.schema.Bucket.DISCOVERY_SCAN;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;
import static udmi.schema.FeatureEnumeration.FeatureStage.PREVIEW;
import static udmi.schema.FeatureEnumeration.FeatureStage.STABLE;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.udmi.util.CleanDateFormat;
import com.google.udmi.util.JsonUtil;
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
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Enumerate;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureEnumeration;

/**
 * Validation tests for discovery scan and enumeration capabilities.
 */
public class DiscoverySequences extends SequenceBase {

  public static final int SCAN_START_DELAY_SEC = 10;
  private static final int SCAN_ITERATIONS = 2;
  private HashMap<String, Date> previousGenerations;
  private Set<String> families;

  private static boolean isActive(Entry<String, FeatureEnumeration> entry) {
    return Optional.ofNullable(entry.getValue().stage).orElse(STABLE).compareTo(BETA) >= 0;
  }

  private DiscoveryEvent runEnumeration(Enumerate enumerate) {
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.enumerate = enumerate;
    untilTrue("enumeration not active", () -> deviceState.discovery.generation == null);

    Date startTime = SemanticDate.describe("generation start time", CleanDateFormat.cleanDate());
    deviceConfig.discovery.generation = startTime;
    info("Starting empty enumeration at " + JsonUtil.getTimestamp(startTime));
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
    info("Received discovery generation " + JsonUtil.getTimestamp(event.generation));
    assertEquals("matching event generation", startTime, event.generation);
    return event;
  }

  private void checkSelfEnumeration(DiscoveryEvent event, Enumerate enumerate) {
    if (isTrue(enumerate.families)) {
      Set<String> models = Optional.ofNullable(deviceMetadata.localnet)
          .map(localnet -> localnet.families.keySet()).orElse(null);
      Set<String> events = Optional.ofNullable(event.families).map(Map::keySet).orElse(null);
      checkThat("family enumeration matches", () -> models.size() == events.size());
    } else {
      checkThat("no family enumeration", () -> event.families == null);
    }

    if (isTrue(enumerate.features)) {
      checkFeatureEnumeration(event.features);
    } else {
      checkThat("no feature enumeration", () -> event.features == null);
    }

    if (isTrue(enumerate.uniqs)) {
      int expectedSize = Optional.ofNullable(deviceMetadata.pointset.points).map(HashMap::size)
          .orElse(0);
      checkThat("enumerated point count matches", () -> event.uniqs.size() == expectedSize);
    } else {
      checkThat("no point enumeration", () -> event.uniqs == null);
    }
  }

  private void checkFeatureEnumeration(Map<String, FeatureEnumeration> features) {
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

  @Test
  @Feature(bucket = ENUMERATION, stage = PREVIEW)
  public void empty_enumeration() {
    Enumerate enumerate = new Enumerate();
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = ENUMERATION_POINTSET, stage = ALPHA)
  public void pointset_enumeration() {
    if (!catchToFalse(() -> deviceMetadata.pointset.points != null)) {
      throw new AssumptionViolatedException("No metadata pointset points defined");
    }
    Enumerate enumerate = new Enumerate();
    enumerate.uniqs = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION_FEATURES, stage = PREVIEW)
  public void feature_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.features = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION_FAMILIES, stage = ALPHA)
  public void family_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.families = true;
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test
  @Feature(bucket = ENUMERATION, stage = ALPHA)
  public void multi_enumeration() {
    Enumerate enumerate = new Enumerate();
    enumerate.families = isBucketEnabled(ENUMERATION_FAMILIES);
    enumerate.features = isBucketEnabled(ENUMERATION_FEATURES);
    enumerate.uniqs = isBucketEnabled(ENUMERATION_POINTSET);
    DiscoveryEvent event = runEnumeration(enumerate);
    checkSelfEnumeration(event, enumerate);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  public void single_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate(
        Date.from(Instant.now().plusSeconds(SCAN_START_DELAY_SEC)));
    boolean shouldEnumerate = true;
    scheduleScan(startTime, null, shouldEnumerate);
    untilTrue("scheduled scan start",
        () -> families.stream().anyMatch(familyScanActivated(startTime))
            || families.stream().anyMatch(this::stateGenerationMismatch)
            || !deviceState.timestamp.before(startTime));
    if (deviceState.timestamp.before(startTime)) {
      warning("scan started before activation: " + deviceState.timestamp + " < " + startTime);
      assertFalse("premature activation",
          families.stream().anyMatch(familyScanActivated(startTime)));
      assertFalse("premature generation",
          families.stream().anyMatch(this::stateGenerationMismatch));
      fail("unknown reason");
    }
    untilTrue("scan activation", () -> families.stream().allMatch(familyScanActivated(startTime)));
    untilTrue("scan completed", () -> families.stream().allMatch(familyScanComplete(startTime)));
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(
        DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    Set<String> eventFamilies = receivedEvents.stream()
        .flatMap(event -> event.families.keySet().stream())
        .collect(Collectors.toSet());
    assertTrue("all requested families present", eventFamilies.containsAll(families));
  }

  private void checkEnumeration(List<DiscoveryEvent> receivedEvents, boolean shouldEnumerate) {
    Predicate<DiscoveryEvent> hasPoints = event -> event.uniqs != null
        && !event.uniqs.isEmpty();
    if (shouldEnumerate) {
      assertTrue("with enumeration", receivedEvents.stream().allMatch(hasPoints));
    } else {
      assertTrue("sans enumeration", receivedEvents.stream().noneMatch(hasPoints));
    }
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(bucket = DISCOVERY_SCAN, stage = ALPHA)
  public void periodic_scan() {
    initializeDiscovery();
    Date startTime = CleanDateFormat.cleanDate();
    boolean shouldEnumerate = true;
    scheduleScan(startTime, SCAN_START_DELAY_SEC, shouldEnumerate);
    Instant endTime = Instant.now().plusSeconds(SCAN_START_DELAY_SEC * SCAN_ITERATIONS);
    untilUntrue("scan iterations", () -> Instant.now().isBefore(endTime));
    String oneFamily = families.iterator().next();
    Date finishTime = deviceState.discovery.families.get(oneFamily).generation;
    assertTrue("premature termination",
        families.stream().noneMatch(familyScanComplete(finishTime)));
    List<DiscoveryEvent> receivedEvents = popReceivedEvents(DiscoveryEvent.class);
    checkEnumeration(receivedEvents, shouldEnumerate);
    int expected = SCAN_ITERATIONS * families.size();
    int received = receivedEvents.size();
    assertTrue("number responses received", received >= expected && received <= expected + 1);
  }

  private void initializeDiscovery() {
    families = catchToNull(() -> deviceMetadata.discovery.families.keySet());
    if (families == null || families.isEmpty()) {
      throw new AssumptionViolatedException("No discovery families configured");
    }
    deviceConfig.discovery = new DiscoveryConfig();
    deviceConfig.discovery.families = new HashMap<>();
    untilTrue("all scans not active", () -> families.stream().noneMatch(familyScanActivated(null)));
    previousGenerations = new HashMap<>();
    families.forEach(family -> previousGenerations.put(family, getStateFamilyGeneration(family)));
  }

  private void scheduleScan(Date startTime, Integer scanIntervalSec, boolean enumerate) {
    info("Scan start scheduled for " + startTime);
    families.forEach(family -> {
      getConfigFamily(family).generation = SemanticDate.describe("family generation", startTime);
      getConfigFamily(family).enumerate = enumerate;
      getConfigFamily(family).scan_interval_sec = scanIntervalSec;
    });
    popReceivedEvents(DiscoveryEvent.class);  // Clear out any previously received events
  }

  private FamilyDiscoveryConfig getConfigFamily(String family) {
    return deviceConfig.discovery.families.computeIfAbsent(family,
        adding -> new FamilyDiscoveryConfig());
  }

  private Date getStateFamilyGeneration(String family) {
    return catchToNull(() -> getStateFamily(family).generation);
  }

  private boolean stateGenerationMismatch(String family) {
    return !Objects.equals(previousGenerations.get(family), getStateFamilyGeneration(family));
  }

  private FamilyDiscoveryState getStateFamily(String family) {
    return deviceState.discovery.families.get(family);
  }

  private Predicate<String> familyScanActive(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        && CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<String> familyScanActivated(Date startTime) {
    return family -> catchToFalse(() -> getStateFamily(family).active
        || CleanDateFormat.dateEquals(getStateFamily(family).generation, startTime));
  }

  private Predicate<? super String> familyScanComplete(Date startTime) {
    return familyScanActivated(startTime).and(familyScanActive(startTime).negate());
  }
}
