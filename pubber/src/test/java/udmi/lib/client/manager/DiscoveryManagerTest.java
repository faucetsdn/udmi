package udmi.lib.client.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static udmi.lib.client.manager.DiscoveryScan.forFamily;
import static udmi.schema.Category.DISCOVERY_FAMILY_SCAN;
import static udmi.schema.FamilyDiscoveryState.Phase.ACTIVE;
import static udmi.schema.FamilyDiscoveryState.Phase.PENDING;
import static udmi.schema.FamilyDiscoveryState.Phase.STOPPED;

import daq.pubber.impl.manager.PubberDiscoveryManager;
import daq.pubber.impl.provider.PubberBacnetProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.Level;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;

/**
 * Unit tests for {@link DiscoveryManager} and {@link DiscoveryScheduler}, including named scans,
 * deferred sporadic scans, and lock-free provider interactions.
 */
public class DiscoveryManagerTest {

  private static final String BACNET = "bacnet";
  private static final String VENDOR = "vendor";
  private static final Instant START_TIME = Instant.now().minusSeconds(60);

  private final FamilyProvider bacnetProvider = mockScanProvider();
  private final FamilyProvider vendorProvider = mockScanProvider();
  private final FakeDiscoveryManager manager = new FakeDiscoveryManager(
      Map.of(BACNET, bacnetProvider, VENDOR, vendorProvider));
  private final DiscoveryScheduler scheduler = manager.getDiscoveryScheduler();

  private static FamilyProvider mockScanProvider() {
    FamilyProvider provider = mock(FamilyProvider.class);
    when(provider.supportsDiscoveryScan()).thenReturn(true);
    return provider;
  }

  @Test
  public void scanStopsAfterConfiguredDuration() {
    assertEquals(60, scheduledStopSec(60));
  }

  @Test
  public void scanStopsAfterDefaultDurationWhenUnset() {
    assertEquals(DiscoveryManager.SCAN_DURATION_SEC, scheduledStopSec(null));
  }

  private long scheduledStopSec(Integer scanDurationSec) {
    FamilyDiscoveryConfig familyConfig = new FamilyDiscoveryConfig();
    familyConfig.scan_duration_sec = scanDurationSec;
    DiscoveryConfig discoveryConfig = new DiscoveryConfig();
    discoveryConfig.families = new HashMap<>(Map.of(BACNET, familyConfig));
    scheduler.setDiscoveryConfig(discoveryConfig);
    scheduler.setDiscoveryState(new DiscoveryState());

    long startMs = manager.now.toEpochMilli();
    scheduler.startDiscoveryScan(forFamily(BACNET), new Date(startMs));

    return (manager.futures.get(0).time().getTime() - startMs) / 1000;
  }

  @Test
  public void namedScansOfSameFamilyScheduleIndependently() {
    manager.updateConfig(discovery(null, Map.of(
        "nightly", periodicScan(BACNET, 3600),
        "adhoc", oneShotScan(BACNET))));

    assertNull(manager.getDiscoveryState().families);
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(2, manager.futures.size());
  }

  @Test
  public void namedScanEventsCarryScanName() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);

    assertEquals(ACTIVE, scanState("adhoc").phase);
    providerPublisher(bacnetProvider).accept("AHU-1", discovered("1042"));

    assertEquals(2, manager.published.size());
    manager.published.forEach(event -> {
      assertEquals(BACNET, event.family);
      assertEquals("adhoc", event.scan);
    });
  }

  @Test
  public void familyScanEventsHaveNoScanName() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.runFuture(0);
    providerPublisher(bacnetProvider).accept("AHU-1", discovered("1042"));

    assertEquals(2, manager.published.size());
    manager.published.forEach(event -> {
      assertEquals(BACNET, event.family);
      assertNull(event.scan);
    });
  }

  @Test
  public void namedScanWithoutFamilyIsRejected() {
    manager.updateConfig(discovery(null, Map.of(
        "broken", oneShotScan(null),
        "adhoc", oneShotScan(BACNET))));

    assertScanError(scanState("broken"), "missing required family");
    assertEquals(PENDING, scanState("adhoc").phase);
  }

  @Test
  public void familiesEntryWithFamilyIsRejected() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)), null));

    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    assertTrue(manager.futures.isEmpty());
  }

  @Test
  public void namedScanWithUnsupportedFamilyIsRejected() {
    manager.updateConfig(discovery(null, Map.of("modbus", oneShotScan("modbus"))));

    assertScanError(scanState("modbus"), "not supported");
    assertTrue(manager.futures.isEmpty());
  }

  @Test
  public void sporadicScanOfBusyFamilyDefersUntilActiveScanCompletes() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    final int familyCompletion = manager.futures.size() - 1;
    manager.runFuture(1);

    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(BACNET).phase);
    assertEquals(PENDING, scanState("adhoc").phase);
    assertNull(scanState("adhoc").status);
    verify(bacnetProvider, times(1)).startScan(any(), any());

    // Unrelated config re-apply preserves the deferred pending scan.
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    assertEquals(PENDING, scanState("adhoc").phase);

    manager.runFuture(familyCompletion);
    assertEquals(STOPPED, manager.getDiscoveryState().families.get(BACNET).phase);
    assertEquals(ACTIVE, scanState("adhoc").phase);
    assertNull(scanState("adhoc").status);
    verify(bacnetProvider, times(2)).startScan(any(), any());
  }

  @Test
  public void busyPeriodicScanRetriesAtNextInterval() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    final int adhocCompletion = manager.futures.size() - 1;
    final Date failedGeneration = scanState("nightly").generation;
    manager.now = failedGeneration.toInstant();
    manager.runFuture(manager.futureIndexFor("nightly"));

    assertScanError(scanState("nightly"), "busy");
    Date retryGeneration = Date.from(failedGeneration.toInstant().plusSeconds(3600));
    ScheduledEntry retry = manager.futures.get(manager.futures.size() - 1);
    assertEquals(retryGeneration, retry.time());

    manager.runFuture(adhocCompletion);
    manager.now = retryGeneration.toInstant().minusMillis(1);
    retry.task().run();

    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(retryGeneration, scanState("nightly").generation);
    assertNull(scanState("nightly").status);
    manager.runFuture(manager.futures.size() - 1);
    assertEquals(ACTIVE, scanState("nightly").phase);
  }

  @Test
  public void sporadicFamilyScanDefersAndPeriodicFamilyScanFailsBusyWhenNamedScanHoldsFamily() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(1);
    final int adhocCompletion = manager.futures.size() - 1;
    manager.runFuture(0);

    assertEquals(ACTIVE, scanState("adhoc").phase);
    assertEquals(PENDING, manager.getDiscoveryState().families.get(BACNET).phase);
    assertNull(manager.getDiscoveryState().families.get(BACNET).status);

    manager.runFuture(adhocCompletion);
    assertEquals(STOPPED, scanState("adhoc").phase);
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(BACNET).phase);

    // Now verify a periodic families.bacnet slot hitting an active named scan reports busy.
    final int familyCompletion = manager.futures.size() - 1;
    manager.runFuture(familyCompletion);
    assertEquals(STOPPED, manager.getDiscoveryState().families.get(BACNET).phase);

    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(45));
    FamilyDiscoveryConfig periodicFamily = periodicScan(null, 60);
    periodicFamily.generation = Date.from(START_TIME.plusSeconds(50));
    manager.now = START_TIME.plusSeconds(40);
    manager.updateConfig(discovery(Map.of(BACNET, periodicFamily), Map.of("blocker", blocker)));

    manager.now = blocker.generation.toInstant();
    manager.runFuture(manager.futureIndexFor("blocker"));
    manager.now = periodicFamily.generation.toInstant();
    manager.runFuture(manager.futureIndexForFamily(BACNET));

    assertScanError(manager.getDiscoveryState().families.get(BACNET), "busy");
  }

  @Test
  public void deferredSporadicScansRunFifoAndRespectRescheduleOrBlockerRemoval() {
    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(10));
    FamilyDiscoveryConfig first = oneShotScan(BACNET);
    first.generation = Date.from(START_TIME.plusSeconds(11));
    FamilyDiscoveryConfig second = oneShotScan(BACNET);
    second.generation = Date.from(START_TIME.plusSeconds(12));
    FamilyDiscoveryConfig rescheduledFuture = oneShotScan(BACNET);
    rescheduledFuture.generation = Date.from(START_TIME.plusSeconds(13));

    manager.now = START_TIME.plusSeconds(5);
    manager.updateConfig(discovery(null, Map.of(
        "blocker", blocker,
        "first", first,
        "second", second,
        "later", rescheduledFuture)));

    manager.now = START_TIME.plusSeconds(10);
    manager.runFuture(manager.futureIndexFor("blocker"));
    manager.now = START_TIME.plusSeconds(11);
    manager.runFuture(manager.futureIndexFor("first"));
    manager.now = START_TIME.plusSeconds(12);
    manager.runFuture(manager.futureIndexFor("second"));
    manager.now = START_TIME.plusSeconds(13);
    manager.runFuture(manager.futureIndexFor("later"));

    assertEquals(ACTIVE, scanState("blocker").phase);
    assertEquals(PENDING, scanState("first").phase);
    assertEquals(PENDING, scanState("second").phase);
    assertEquals(PENDING, scanState("later").phase);

    // Reschedule "later" into the future and remove "blocker": "first" starts immediately,
    // "second" stays deferred behind "first", and "later" waits for its future timestamp.
    FamilyDiscoveryConfig movedFuture = oneShotScan(BACNET);
    movedFuture.generation = Date.from(START_TIME.plusSeconds(300));
    manager.updateConfig(discovery(null, Map.of(
        "first", first,
        "second", second,
        "later", movedFuture)));

    assertFalse(manager.getDiscoveryState().scans.containsKey("blocker"));
    assertEquals(ACTIVE, scanState("first").phase);
    assertEquals(PENDING, scanState("second").phase);
    assertEquals(PENDING, scanState("later").phase);

    final int firstCompletion = manager.futures.size() - 1;
    manager.now = START_TIME.plusSeconds(23);
    manager.runFuture(firstCompletion);

    assertEquals(STOPPED, scanState("first").phase);
    assertEquals(ACTIVE, scanState("second").phase);
    assertEquals(PENDING, scanState("later").phase);

    final int secondCompletion = manager.futures.size() - 1;
    manager.now = START_TIME.plusSeconds(33);
    manager.runFuture(secondCompletion);

    assertEquals(STOPPED, scanState("second").phase);
    assertEquals(PENDING, scanState("later").phase);
    assertFalse(scheduler.hasActiveScans());
  }

  @Test
  public void familiesEntryWithUnsupportedFamilyDoesNotErrorAndLeavesScansNull() {
    manager.updateConfig(discovery(Map.of("modbus", oneShotScan(null)), null));

    FamilyDiscoveryState modbusState = manager.getDiscoveryState().families.get("modbus");
    assertEquals(PENDING, modbusState.phase);
    assertNull(modbusState.status);
    assertNull(manager.getDiscoveryState().scans);
    assertFalse(manager.sawEmptyScansMap);

    manager.runFuture(0);
    assertEquals(ACTIVE, modbusState.phase);
    assertNull(modbusState.status);

    manager.runFuture(1);
    assertEquals(STOPPED, modbusState.phase);
    assertNull(modbusState.status);
  }

  @Test
  public void nullDiscoveryConfigStopsActiveScansAndClearsState() {
    manager.updateConfig(discovery(Map.of(VENDOR, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    manager.runFuture(1);
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(VENDOR).phase);
    assertEquals(ACTIVE, scanState("adhoc").phase);

    manager.updateConfig(null);

    verify(vendorProvider).stopScan();
    verify(bacnetProvider).stopScan();
    assertFalse(scheduler.hasActiveScans());
    assertFalse(scheduler.hasScanSchedules());
    assertNull(manager.getDiscoveryState());
  }

  @Test
  public void nullMapEntryValuesHandledSafely() {
    Map<String, FamilyDiscoveryConfig> families = new HashMap<>();
    families.put(BACNET, null);
    Map<String, FamilyDiscoveryConfig> scans = new HashMap<>();
    scans.put("adhoc", null);
    scans.put("ok", oneShotScan(BACNET));

    manager.updateConfig(discovery(families, scans));

    assertFalse(manager.getDiscoveryState().families.containsKey(BACNET));
    assertScanError(scanState("adhoc"), "missing required family");
    assertEquals(PENDING, scanState("ok").phase);
  }

  @Test
  public void nextPeriodicStartNeverReturnsLastRunWhenNowBeforeLastRun() {
    Date generation = Date.from(START_TIME);
    Date lastRun = Date.from(START_TIME.plusSeconds(60));
    Instant slightlyBeforeLastRun = lastRun.toInstant().minusMillis(5);
    assertEquals(Date.from(START_TIME.plusSeconds(120)), DiscoveryScheduler.nextPeriodicStart(
        generation, 60, lastRun, slightlyBeforeLastRun));
  }

  @Test
  public void cancellingOrFixingErroredScanClearsStaleErrorStatus() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)),
        Map.of("adhoc", oneShotScan(null))));
    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    assertScanError(scanState("adhoc"), "missing required family");

    FamilyDiscoveryConfig cancelled = oneShotScan(BACNET);
    cancelled.generation = Date.from(START_TIME.minusSeconds(10));
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)),
        Map.of("adhoc", cancelled)));

    FamilyDiscoveryState fixedFamily = manager.getDiscoveryState().families.get(BACNET);
    assertEquals(PENDING, fixedFamily.phase);
    assertNull(fixedFamily.status);

    FamilyDiscoveryState cancelledScan = scanState("adhoc");
    assertEquals(STOPPED, cancelledScan.phase);
    assertEquals(cancelled.generation, cancelledScan.generation);
    assertNull(cancelledScan.status);
  }

  @Test
  public void futurePeriodicGenerationStartsAtGeneration() {
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = Date.from(manager.now.plusSeconds(9000));
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(nightly.generation, scanState("nightly").generation);
    assertEquals(nightly.generation, manager.futures.get(0).time());
  }

  @Test
  public void activePeriodicScanIgnoresReappliedConfig() {
    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));
    Date slot = scanState("nightly").generation;
    manager.now = slot.toInstant();
    manager.runFuture(0);
    assertEquals(ACTIVE, scanState("nightly").phase);
    final int scheduled = manager.futures.size();

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));

    assertEquals(ACTIVE, scanState("nightly").phase);
    assertEquals(slot, scanState("nightly").generation);
    assertEquals(scheduled, manager.futures.size());

    manager.now = slot.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(scheduled - 1);
    verify(bacnetProvider).stopScan();
    assertEquals(2, manager.published.size());
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(slot.toInstant().plusSeconds(3600), scanState("nightly").generation.toInstant());
  }

  @Test
  public void periodicStartRunsAtGenerationWhenAfterStartTime() {
    Date generation = Date.from(START_TIME.plusSeconds(10));
    Instant now = START_TIME.plusSeconds(12);
    assertEquals(generation, DiscoveryScheduler.nextPeriodicStart(
        generation, 60, Date.from(START_TIME), now));
  }

  @Test
  public void periodicStartWaitsForNextSlotWhenGenerationAtOrBeforeStartTime() {
    Date generation = Date.from(START_TIME);
    Instant now = START_TIME.plusSeconds(10);
    assertEquals(Date.from(START_TIME.plusSeconds(60)), DiscoveryScheduler.nextPeriodicStart(
        generation, 60, Date.from(START_TIME), now));
  }

  @Test
  public void periodicStartCatchesUpSlotAfterLateCompletion() {
    Date generation = Date.from(START_TIME);
    Instant lateCompletion = START_TIME.plusSeconds(60).plusMillis(300);
    assertEquals(Date.from(START_TIME.plusSeconds(60)), DiscoveryScheduler.nextPeriodicStart(
        generation, 60, generation, lateCompletion));
  }

  @Test
  public void periodicPendingSlotStaysNoOpPastSlotBoundary() {
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 60);
    nightly.generation = Date.from(START_TIME.plusSeconds(10));
    manager.now = START_TIME.plusSeconds(5);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));
    assertEquals(nightly.generation, scanState("nightly").generation);

    manager.now = START_TIME.plusSeconds(10).plusMillis(20);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    assertEquals(1, manager.futures.size());
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(nightly.generation, scanState("nightly").generation);
  }

  @Test
  public void activeFamilyScanBecomingInvalidStopsProviderAndReleasesFamily() {
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.runFuture(0);
    assertEquals(ACTIVE, manager.getDiscoveryState().families.get(BACNET).phase);

    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(BACNET)),
        Map.of("adhoc", oneShotScan(BACNET))));

    assertScanError(manager.getDiscoveryState().families.get(BACNET), "must not specify family");
    verify(bacnetProvider).stopScan();
    assertFalse(scheduler.hasActiveScans());
    manager.runFuture(manager.futures.size() - 1);
    assertEquals(ACTIVE, scanState("adhoc").phase);
  }

  @Test
  public void lateProviderCallbackAfterRemovalOrCompletionIsIgnored() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    assertEquals(1, manager.published.size());

    manager.updateConfig(discovery(null, Map.of()));
    publisher.accept("AHU-1", discovered("1042"));

    assertEquals(1, manager.published.size());
    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
  }

  @Test
  public void stopScanJoiningPublisherThreadDoesNotDeadlock() throws Exception {
    AtomicReference<BiConsumer<String, DiscoveryEvents>> captured = new AtomicReference<>();
    doAnswer(invocation -> {
      captured.set(invocation.getArgument(1));
      return null;
    }).when(bacnetProvider).startScan(any(), any());

    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    assertEquals(1, manager.published.size());

    CountDownLatch inPublish = new CountDownLatch(1);
    CountDownLatch hostLockHeld = new CountDownLatch(1);
    manager.onPostDiscoveryProcess = () -> {
      inPublish.countDown();
      try {
        assertTrue(hostLockHeld.await(2, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };

    AtomicReference<Throwable> workerError = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      try {
        captured.get().accept("AHU-1", discovered("1042"));
      } catch (Throwable t) {
        workerError.set(t);
      }
    });
    worker.start();
    assertTrue(inPublish.await(2, TimeUnit.SECONDS));

    doAnswer(invocation -> {
      worker.join(2000);
      assertFalse("Provider worker deadlocked in publisher during stopScan()", worker.isAlive());
      return null;
    }).when(bacnetProvider).stopScan();

    manager.getHost().withStateLock(() -> {
      hostLockHeld.countDown();
      manager.updateConfig(discovery(null, Map.of()));
    });
    manager.onPostDiscoveryProcess = null;

    assertNull(workerError.get());
    assertEquals(2, manager.published.size());
    assertFalse(scheduler.hasActiveScans());
  }

  @Test
  public void scheduleFutureRejectedOnShutdownLeavesSchedulerClean() {
    manager.rejectSchedules = true;
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    assertNull(scanState("adhoc").phase);
    assertFalse(scheduler.hasScanSchedules());

    scheduler.startDiscoveryScan(DiscoveryScan.forScan("adhoc"),
        manager.getDiscoveryConfig().scans.get("adhoc").generation);
    assertFalse(scheduler.hasActiveScans());
    assertTrue(manager.published.isEmpty());
  }

  @Test
  public void busyErrorPreservedAcrossUnrelatedConfigUpdateUntilRetryOrReschedule() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    final Date failedGeneration = scanState("nightly").generation;
    manager.now = failedGeneration.toInstant();
    manager.runFuture(manager.futureIndexFor("nightly"));
    assertScanError(scanState("nightly"), "busy");
    final int futuresAfterBusy = manager.futures.size();

    manager.now = failedGeneration.toInstant().plusSeconds(5);
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 3600),
        "vendor_scan", oneShotScan(VENDOR))));

    assertScanError(scanState("nightly"), "busy");
    assertEquals(failedGeneration, scanState("nightly").generation);
    assertEquals(futuresAfterBusy + 1, manager.futures.size());

    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(BACNET, 1800))));
    assertEquals(PENDING, scanState("nightly").phase);
    assertNull(scanState("nightly").status);
    assertEquals(Date.from(failedGeneration.toInstant().plusSeconds(1800)),
        scanState("nightly").generation);
  }

  @Test
  public void removingActiveScanStopsItAndRemovesState() {
    manager.updateConfig(discovery(null, Map.of(
        "adhoc", oneShotScan(BACNET),
        "nightly", periodicScan(VENDOR, 3600))));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(VENDOR, 3600))));

    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
    assertEquals(PENDING, scanState("nightly").phase);
    verify(bacnetProvider).stopScan();
  }

  @Test
  public void changingScanFamilyRestartsScan() {
    DiscoveryConfig config = discovery(null, Map.of("adhoc", oneShotScan(BACNET)));
    manager.updateConfig(config);
    manager.runFuture(0);
    final FamilyDiscoveryState original = scanState("adhoc");

    // Mutate family in-place on the same FamilyDiscoveryConfig instance to verify
    // configuredScanFamilies tracking does not rely on distinct object references.
    config.scans.get("adhoc").family = VENDOR;
    manager.updateConfig(config);

    verify(bacnetProvider).stopScan();
    assertNotSame(original, scanState("adhoc"));
    assertEquals(PENDING, scanState("adhoc").phase);
    manager.runFuture(manager.futures.size() - 1);
    verify(vendorProvider).startScan(any(), any());
  }

  @Test
  public void staleCompletionOfReplacedScanIsIgnored() {
    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(0);
    final int completion = manager.futures.size() - 1;

    FamilyDiscoveryConfig changed = oneShotScan(VENDOR);
    changed.generation = manager.getDiscoveryConfig().scans.get("adhoc").generation;
    manager.updateConfig(discovery(null, Map.of("adhoc", changed)));
    assertTrue(manager.futures.get(completion).isCancelled());
    manager.runFuture(manager.futures.size() - 1);
    manager.futures.get(completion).task().run();

    assertEquals(ACTIVE, scanState("adhoc").phase);
    verify(vendorProvider, never()).stopScan();
  }

  @Test
  public void identicalConfigIsNoOp() {
    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));
    Date generation = scanState("nightly").generation;

    manager.updateConfig(discovery(null, Map.of("nightly", periodicScan(BACNET, 3600))));

    assertEquals(1, manager.futures.size());
    assertEquals(generation, scanState("nightly").generation);
  }

  @Test
  public void reschedulingPendingOneShotToEarlierPostStartGenerationRunsIt() {
    FamilyDiscoveryConfig distant = oneShotScan(BACNET);
    distant.generation = Date.from(START_TIME.plusSeconds(3600));
    manager.updateConfig(discovery(null, Map.of("adhoc", distant)));
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(distant.generation, scanState("adhoc").generation);

    FamilyDiscoveryConfig sooner = oneShotScan(BACNET);
    sooner.generation = Date.from(START_TIME.plusSeconds(60));
    manager.updateConfig(discovery(null, Map.of("adhoc", sooner)));

    assertTrue(manager.futures.get(0).isCancelled());
    assertEquals(PENDING, scanState("adhoc").phase);
    assertEquals(sooner.generation, scanState("adhoc").generation);
    manager.runFuture(manager.futureIndexFor("adhoc"));
    assertEquals(ACTIVE, scanState("adhoc").phase);
  }

  @Test
  public void providerStartAndStopExceptionsAreIsolatedAndReleaseFamily() {
    doThrow(new RuntimeException("boom start")).when(bacnetProvider).startScan(any(), any());
    doThrow(new RuntimeException("boom stop")).when(vendorProvider).stopScan();

    FamilyDiscoveryConfig vendorScan = oneShotScan(VENDOR);
    vendorScan.generation = Date.from(START_TIME.plusSeconds(45));
    manager.updateConfig(discovery(null, Map.of(
        "bad_bacnet", oneShotScan(BACNET),
        "vendor_scan", vendorScan)));
    assertEquals(1, manager.stateUpdates);

    manager.runFuture(manager.futureIndexFor("bad_bacnet"));
    assertScanError(scanState("bad_bacnet"), "failed to start");
    assertFalse(scheduler.isFamilyActive(BACNET));

    manager.runFuture(manager.futureIndexFor("vendor_scan"));
    assertEquals(ACTIVE, scanState("vendor_scan").phase);
    manager.updateConfig(null);
    assertNull(manager.getDiscoveryState());
    assertFalse(scheduler.hasActiveScans());
  }

  @Test
  public void pubberDiscoveryManagerRejectsLocalnetOnlyProviderAndStopsOnShutdown() {
    FamilyProvider ipOnlyProvider = new FamilyProvider() {};
    PubberBacnetProvider scanCapableProvider = mock(PubberBacnetProvider.class);
    when(scanCapableProvider.supportsDiscoveryScan()).thenReturn(true);
    ManagerHost host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod()
        .getName()) {
      case "getLocalnetProvider" -> switch ((String) invocation.getArgument(0)) {
        case "ipv4" -> ipOnlyProvider;
        case BACNET -> scanCapableProvider;
        default -> null;
      };
      case "getStartTime" -> Date.from(START_TIME);
      case "withStateLock" -> {
        ((Runnable) invocation.getArgument(0)).run();
        yield null;
      }
      case "tryWithStateLock" -> {
        ((Runnable) invocation.getArgument(0)).run();
        yield true;
      }
      default -> null;
    });
    PubberConfiguration pubberConfig = new PubberConfiguration();
    pubberConfig.deviceId = "AHU-1";
    PubberDiscoveryManager pubberManager = new PubberDiscoveryManager(host, pubberConfig, null);
    DiscoveryScheduler pubberScheduler = pubberManager.getDiscoveryScheduler();
    FamilyDiscoveryConfig adhocScan = oneShotScan(BACNET);
    adhocScan.generation = Date.from(Instant.now().plusSeconds(30));
    try {
      assertNull(pubberManager.discoveryProvider("ipv4"));
      assertEquals(scanCapableProvider, pubberManager.discoveryProvider(BACNET));

      pubberManager.updateConfig(discovery(null, Map.of(
          "ip_scan", oneShotScan("ipv4"),
          "adhoc", adhocScan)));
      assertScanError(pubberManager.getDiscoveryState().scans.get("ip_scan"), "not supported");
      assertEquals(PENDING, pubberManager.getDiscoveryState().scans.get("adhoc").phase);

      pubberScheduler.startDiscoveryScan(DiscoveryScan.forScan("adhoc"), adhocScan.generation);
      assertEquals(ACTIVE, pubberManager.getDiscoveryState().scans.get("adhoc").phase);
      verify(scanCapableProvider).startScan(any(), any());
    } finally {
      pubberManager.shutdown();
    }
    verify(scanCapableProvider).stopScan();
    assertFalse(pubberScheduler.hasActiveScans());
    assertFalse(pubberScheduler.hasScanSchedules());
  }

  @Test
  public void updateStateRecoversAfterException() {
    manager.throwOnNextUpdateState = true;
    try {
      manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    } catch (RuntimeException expected) {
      assertEquals("simulated state failure", expected.getMessage());
    }

    final int before = manager.stateUpdates;
    final int beforeLocks = manager.hostLockEntries;
    scheduler.updateState();
    assertEquals(before + 1, manager.stateUpdates);
    assertEquals(beforeLocks + 1, manager.hostLockEntries);
    assertEquals(2, manager.hostLockEntries);
  }

  @Test
  public void removingIntervalOrRevertingPendingToLastRunCancelsPendingFuture() {
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    BiConsumer<String, DiscoveryEvents> publisher = providerPublisher(bacnetProvider);
    publisher.accept("dev-1", discovered("1001"));
    assertEquals(Integer.valueOf(1), scanState("nightly").active_count);

    manager.now = t0.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(1);
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(Integer.valueOf(1), scanState("nightly").active_count);
    final int nextLoopFuture = manager.futures.size() - 1;
    assertFalse(manager.futures.get(nextLoopFuture).isCancelled());

    FamilyDiscoveryConfig stoppedLoop = oneShotScan(BACNET);
    stoppedLoop.generation = t0;
    manager.updateConfig(discovery(null, Map.of("nightly", stoppedLoop)));

    assertTrue(manager.futures.get(nextLoopFuture).isCancelled());
    assertEquals(STOPPED, scanState("nightly").phase);
    assertEquals(t0, scanState("nightly").generation);
    assertNull(scanState("nightly").active_count);

    FamilyDiscoveryConfig futureScan = oneShotScan(VENDOR);
    futureScan.generation = Date.from(START_TIME.plusSeconds(120));
    manager.updateConfig(discovery(null, Map.of("adhoc", futureScan)));
    final int adhocFuture = manager.futureIndexFor("adhoc");
    FamilyDiscoveryConfig reverted = oneShotScan(VENDOR);
    reverted.generation = Date.from(START_TIME);
    manager.updateConfig(discovery(null, Map.of("adhoc", reverted)));

    assertTrue(manager.futures.get(adhocFuture).isCancelled());
    assertEquals(STOPPED, scanState("adhoc").phase);
    assertEquals(reverted.generation, scanState("adhoc").generation);
  }

  @Test
  public void removingIntervalCancelsBusyRetryAndErrorClearsStaleActiveCount() {
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 60);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    providerPublisher(bacnetProvider).accept("dev-1", discovered("1001"));
    manager.now = t0.toInstant().plusSeconds(DiscoveryManager.SCAN_DURATION_SEC);
    manager.runFuture(1);
    assertEquals(Integer.valueOf(1), scanState("nightly").active_count);
    final int secondSlotStart = manager.futures.size() - 1;

    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(85));
    manager.updateConfig(discovery(null, Map.of("nightly", nightly, "blocker", blocker)));
    manager.now = blocker.generation.toInstant();
    manager.runFuture(manager.futureIndexFor("blocker"));

    Date secondSlot = Date.from(t0.toInstant().plusSeconds(60));
    manager.now = secondSlot.toInstant();
    manager.runFuture(secondSlotStart);
    assertScanError(scanState("nightly"), "busy");
    assertNull(scanState("nightly").active_count);
    final int busyRetryFuture = manager.futures.size() - 1;
    assertFalse(manager.futures.get(busyRetryFuture).isCancelled());

    FamilyDiscoveryConfig noInterval = oneShotScan(BACNET);
    noInterval.generation = secondSlot;
    manager.updateConfig(discovery(null, Map.of("nightly", noInterval, "blocker", blocker)));
    assertTrue(manager.futures.get(busyRetryFuture).isCancelled());
    assertScanError(scanState("nightly"), "busy");
  }

  @Test
  public void staleCheckOrDuplicateStartDoesNotLeakActiveFamily() {
    Date generation = Date.from(START_TIME.plusSeconds(30));
    manager.updateConfig(discovery(Map.of(BACNET, oneShotScan(null)), null));
    manager.updateConfig(null);

    scheduler.checkDiscoveryScan(forFamily(BACNET), generation);
    scheduler.startDiscoveryScan(forFamily(BACNET), generation);
    assertFalse(scheduler.hasActiveScans());
    assertTrue(manager.published.isEmpty());

    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    assertEquals(1, manager.published.size());

    scheduler.startDiscoveryScan(DiscoveryScan.forScan("adhoc"), generation);
    assertEquals(1, manager.published.size());
  }

  @Test
  public void startScanRemovalOrReplacementDoesNotResurrectOrStopReplacement() {
    doAnswer(invocation -> {
      manager.updateConfig(discovery(null, null));
      throw new RuntimeException("aborted on stop");
    }).when(bacnetProvider).startScan(any(), any());

    manager.updateConfig(discovery(null, Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    assertNull(manager.getDiscoveryState().scans);
    assertFalse(scheduler.hasActiveScans());

    FamilyDiscoveryConfig replacement = oneShotScan(VENDOR);
    replacement.generation = Date.from(START_TIME.plusSeconds(60));
    AtomicInteger starts = new AtomicInteger();
    doAnswer(invocation -> {
      if (starts.getAndIncrement() == 0) {
        manager.now = replacement.generation.toInstant();
        manager.updateConfig(discovery(null, Map.of("other", replacement)));
        manager.runFuture(manager.futureIndexFor("other"));
      }
      return null;
    }).when(vendorProvider).startScan(any(), any());

    FamilyDiscoveryConfig first = oneShotScan(VENDOR);
    manager.updateConfig(discovery(null, Map.of("adhoc", first)));
    manager.runFuture(manager.futureIndexFor("adhoc"));

    assertFalse(manager.getDiscoveryState().scans.containsKey("adhoc"));
    assertEquals(ACTIVE, scanState("other").phase);
    assertEquals(2, starts.get());
    verify(vendorProvider).stopScan();
  }

  @Test
  public void periodicProviderStartFailurePreservesErrorOnReapplyAndSeparateNamespacesWork() {
    doThrow(new RuntimeException("boom")).when(bacnetProvider).startScan(any(), any());
    Date t0 = Date.from(START_TIME.plusSeconds(30));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = t0;
    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));

    manager.now = t0.toInstant();
    manager.runFuture(0);
    assertScanError(scanState("nightly"), "failed to start");
    final int retryIdx = manager.futures.size() - 1;

    manager.now = t0.toInstant().plusSeconds(1);
    manager.updateConfig(discovery(null, Map.of("nightly", nightly)));
    assertScanError(scanState("nightly"), "failed to start");
    assertFalse(manager.futures.get(retryIdx).isCancelled());

    Date retryAt = Date.from(t0.toInstant().plusSeconds(3600));
    manager.now = retryAt.toInstant();
    manager.runFuture(retryIdx);
    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(retryAt, scanState("nightly").generation);

    // A named scan may share its key name ("bacnet") with families.bacnet.
    manager.updateConfig(discovery(
        Map.of(BACNET, oneShotScan(null)),
        Map.of(BACNET, oneShotScan(VENDOR))));
    assertEquals(PENDING, manager.getDiscoveryState().families.get(BACNET).phase);
    assertEquals(PENDING, scanState(BACNET).phase);
  }

  @Test
  public void deferredSporadicReconfiguredToPeriodicReArmsFutureAndEarlyWakeupRuns() {
    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(20));
    FamilyDiscoveryConfig target = oneShotScan(BACNET);
    target.generation = Date.from(START_TIME.plusSeconds(25));

    manager.now = START_TIME.plusSeconds(20);
    manager.updateConfig(discovery(null, Map.of("blocker", blocker, "target", target)));
    manager.runFuture(manager.futureIndexFor("blocker"));

    // Fire target's timer 2ms early (now = 24.998s < 25.000s) so it enters deferredScans,
    // then verify early wakeup does NOT drop it when blocker is replaced.
    manager.now = target.generation.toInstant().minusMillis(2);
    manager.runFuture(manager.futureIndexFor("target"));
    assertEquals(PENDING, scanState("target").phase);

    // Reconfigure deferred sporadic "target" into a periodic scan while removing "blocker":
    // must re-arm a ScheduledFuture instead of wedging in PENDING with no timer.
    FamilyDiscoveryConfig periodicTarget = periodicScan(BACNET, 3600);
    periodicTarget.generation = target.generation;
    manager.now = START_TIME.plusSeconds(30);
    manager.updateConfig(discovery(null, Map.of("target", periodicTarget)));

    assertEquals(PENDING, scanState("target").phase);
    manager.runFuture(manager.futureIndexFor("target"));
    assertEquals(ACTIVE, scanState("target").phase);
  }

  @Test
  public void deferredSporadicWakesEvenAfterBackwardClockStepAndSupersededEnumerationIsIgnored() {
    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(20));
    FamilyDiscoveryConfig target = oneShotScan(BACNET);
    target.generation = Date.from(START_TIME.plusSeconds(25));

    manager.now = START_TIME.plusSeconds(20);
    manager.updateConfig(discovery(null, Map.of("blocker", blocker, "target", target)));
    manager.runFuture(manager.futureIndexFor("blocker"));
    final int blockerCompletion = manager.futures.size() - 1;

    manager.now = target.generation.toInstant().minusMillis(5);
    manager.runFuture(manager.futureIndexFor("target"));
    assertEquals(PENDING, scanState("target").phase);

    manager.runFuture(blockerCompletion);
    assertEquals(ACTIVE, scanState("target").phase);

    // Now stop "target" via updateConfig(config1 with enumeration T1), while inside stopScan()
    // a newer updateConfig(config2 with enumeration null) supersedes config1.
    Date enumGen = Date.from(START_TIME.plusSeconds(40));
    doAnswer(invocation -> {
      DiscoveryConfig newer = discovery(null, Map.of());
      newer.generation = null;
      manager.updateConfig(newer);
      return null;
    }).when(bacnetProvider).stopScan();

    DiscoveryConfig older = discovery(null, Map.of());
    older.generation = enumGen;
    manager.updateConfig(older);

    assertNull(manager.getDiscoveryState().generation);
  }

  @Test
  public void completionFiringDuringProviderStartScanCompletesInOrderAfterStartScanReturns() {
    FamilyDiscoveryConfig first = oneShotScan(BACNET);
    first.generation = Date.from(START_TIME.plusSeconds(20));
    first.scan_duration_sec = 0;
    FamilyDiscoveryConfig second = oneShotScan(BACNET);
    second.generation = Date.from(START_TIME.plusSeconds(25));

    manager.now = first.generation.toInstant();
    manager.updateConfig(discovery(null, Map.of("first", first, "second", second)));
    final int firstStart = manager.futureIndexFor("first");
    final int secondStart = manager.futureIndexFor("second");

    AtomicInteger starts = new AtomicInteger();
    doAnswer(invocation -> {
      if (starts.getAndIncrement() == 0) {
        final int firstCompletion = manager.futures.size() - 1;
        manager.runFuture(firstCompletion);
        manager.now = second.generation.toInstant();
        manager.runFuture(secondStart);
        assertEquals(PENDING, scanState("second").phase);
        verify(bacnetProvider, never()).stopScan();
        BiConsumer<String, DiscoveryEvents> publisher = invocation.getArgument(1);
        publisher.accept("dev-1", discovered("1001"));
      }
      return null;
    }).when(bacnetProvider).startScan(any(), any());

    manager.runFuture(firstStart);

    assertEquals(STOPPED, scanState("first").phase);
    assertEquals(Integer.valueOf(1), scanState("first").active_count);
    assertEquals(ACTIVE, scanState("second").phase);
    assertEquals(2, starts.get());
    verify(bacnetProvider, times(1)).stopScan();
    assertEquals(4, manager.published.size());
    assertEquals("first", manager.published.get(0).scan);
    assertEquals(Integer.valueOf(0), manager.published.get(0).event_no);
    assertEquals("first", manager.published.get(1).scan);
    assertEquals(Integer.valueOf(1), manager.published.get(1).event_no);
    assertEquals("first", manager.published.get(2).scan);
    assertEquals(Integer.valueOf(-2), manager.published.get(2).event_no);
    assertEquals("second", manager.published.get(3).scan);
    assertEquals(Integer.valueOf(0), manager.published.get(3).event_no);
  }

  @Test
  public void clearingScansOrFamiliesWhileStoppingActiveScanNeverPublishesEmptyMap() {
    manager.updateConfig(discovery(
        Map.of(VENDOR, oneShotScan(null)),
        Map.of("adhoc", oneShotScan(BACNET))));
    manager.runFuture(manager.futureIndexFor("adhoc"));
    manager.runFuture(manager.futureIndexForFamily(VENDOR));

    doAnswer(invocation -> {
      scheduler.updateState();
      return null;
    }).when(bacnetProvider).stopScan();
    doAnswer(invocation -> {
      scheduler.updateState();
      return null;
    }).when(vendorProvider).stopScan();

    manager.updateConfig(discovery(Map.of(VENDOR, oneShotScan(null)), null));
    assertFalse(manager.sawEmptyScansMap);
    assertNull(manager.getDiscoveryState().scans);

    manager.updateConfig(discovery(null, Map.of("other", oneShotScan(BACNET))));
    assertFalse(manager.sawEmptyFamiliesMap);
    assertNull(manager.getDiscoveryState().families);
  }

  @Test
  public void negativeIntervalOrDurationRejectedAndMatchesTargetHandlesNullAddr() {
    FamilyDiscoveryConfig negInterval = oneShotScan(BACNET);
    negInterval.scan_interval_sec = -1;
    FamilyDiscoveryConfig negDuration = oneShotScan(BACNET);
    negDuration.scan_duration_sec = -5;
    FamilyDiscoveryConfig nullAddr = oneShotScan(BACNET);
    nullAddr.addrs = Arrays.asList("1000", null);
    FamilyDiscoveryConfig familyNeg = oneShotScan(null);
    familyNeg.scan_interval_sec = -2;

    manager.updateConfig(discovery(
        Map.of(BACNET, familyNeg),
        Map.of("neg_interval", negInterval, "neg_duration", negDuration, "null_addr", nullAddr)));

    assertScanError(scanState("neg_interval"), "negative scan_interval_sec");
    assertScanError(scanState("neg_duration"), "negative scan_duration_sec");
    assertScanError(scanState("null_addr"), "null target address");
    assertScanError(manager.getDiscoveryState().families.get(BACNET), "negative scan_interval_sec");
    assertFalse(DiscoveryManager.matchesTarget(Set.of("1000"), null));
    assertTrue(DiscoveryManager.matchesTarget(null, null));
  }

  @Test
  public void exceptionDuringStartOrCompletionReleasesFamilyAndStartsDeferredScan() {
    FamilyDiscoveryConfig first = oneShotScan(BACNET);
    first.generation = Date.from(START_TIME.plusSeconds(10));
    FamilyDiscoveryConfig second = oneShotScan(BACNET);
    second.generation = Date.from(START_TIME.plusSeconds(15));
    FamilyDiscoveryConfig third = oneShotScan(BACNET);
    third.generation = Date.from(START_TIME.plusSeconds(20));

    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of(
        "first", first, "second", second, "third", third)));
    final int firstStart = manager.futureIndexFor("first");
    final int secondStart = manager.futureIndexFor("second");
    final int thirdStart = manager.futureIndexFor("third");

    manager.now = first.generation.toInstant();
    manager.throwOnNextUpdateState = true;
    try {
      manager.runFuture(firstStart);
    } catch (RuntimeException expected) {
      assertEquals("While checking for discovery scan start", expected.getMessage());
    }
    assertScanError(scanState("first"), "simulated state failure");
    assertFalse(scheduler.hasActiveScans());

    manager.now = second.generation.toInstant();
    manager.runFuture(secondStart);
    final int secondCompletion = manager.futures.size() - 1;
    manager.now = third.generation.toInstant();
    manager.runFuture(thirdStart);
    assertEquals(ACTIVE, scanState("second").phase);
    assertEquals(PENDING, scanState("third").phase);

    manager.throwOnNextPublish = true;
    try {
      manager.runFuture(secondCompletion);
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("While completing discovery scan"));
    }
    assertEquals(STOPPED, scanState("second").phase);
    assertEquals(ACTIVE, scanState("third").phase);
    assertFalse(manager.sawActiveBeforeStartMarker);
  }

  @Test
  public void advancingPeriodicGenerationClearsRetryErrorAndRemovingGenerationStopsLoop() {
    Date t0 = Date.from(START_TIME.plusSeconds(10));
    FamilyDiscoveryConfig blocker = oneShotScan(BACNET);
    blocker.generation = Date.from(START_TIME.plusSeconds(5));
    FamilyDiscoveryConfig nightly = periodicScan(BACNET, 3600);
    nightly.generation = t0;

    manager.now = START_TIME;
    manager.updateConfig(discovery(null, Map.of("blocker", blocker, "nightly", nightly)));
    manager.now = blocker.generation.toInstant();
    manager.runFuture(manager.futureIndexFor("blocker"));
    final int blockerCompletion = manager.futures.size() - 1;
    manager.now = t0.toInstant();
    manager.runFuture(manager.futureIndexFor("nightly"));
    assertScanError(scanState("nightly"), "busy");

    manager.runFuture(blockerCompletion);

    Date nextSlot = Date.from(t0.toInstant().plusSeconds(3600));
    FamilyDiscoveryConfig advanced = periodicScan(BACNET, 3600);
    advanced.generation = nextSlot;
    manager.now = t0.toInstant().plusSeconds(300);
    manager.updateConfig(discovery(null, Map.of("nightly", advanced)));

    assertEquals(PENDING, scanState("nightly").phase);
    assertEquals(nextSlot, scanState("nightly").generation);
    assertNull(scanState("nightly").status);
    final int nextSlotFuture = manager.futureIndexFor("nightly");

    FamilyDiscoveryConfig noGeneration = periodicScan(BACNET, 3600);
    noGeneration.generation = null;
    manager.updateConfig(discovery(null, Map.of("nightly", noGeneration)));

    assertTrue(manager.futures.get(nextSlotFuture).isCancelled());
    assertFalse(manager.getDiscoveryState().scans.containsKey("nightly"));
    assertFalse(scheduler.hasActiveScans());
    assertFalse(scheduler.hasScanSchedules());
  }

  private FamilyDiscoveryState scanState(String name) {
    return manager.getDiscoveryState().scans.get(name);
  }

  private static void assertScanError(FamilyDiscoveryState state, String messagePart) {
    assertEquals(STOPPED, state.phase);
    assertEquals(DISCOVERY_FAMILY_SCAN, state.status.category);
    assertEquals(Level.ERROR.value(), (int) state.status.level);
    assertNotNull(state.status.timestamp);
    assertTrue(state.status.message, state.status.message.contains(messagePart));
  }

  @SuppressWarnings("unchecked")
  private static BiConsumer<String, DiscoveryEvents> providerPublisher(FamilyProvider provider) {
    ArgumentCaptor<BiConsumer<String, DiscoveryEvents>> publisher =
        ArgumentCaptor.forClass(BiConsumer.class);
    verify(provider).startScan(any(), publisher.capture());
    return publisher.getValue();
  }

  private static DiscoveryEvents discovered(String addr) {
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = addr;
    return event;
  }

  private static DiscoveryConfig discovery(Map<String, FamilyDiscoveryConfig> families,
       Map<String, FamilyDiscoveryConfig> scans) {
    DiscoveryConfig config = new DiscoveryConfig();
    config.families = families == null ? null : new HashMap<>(families);
    config.scans = scans == null ? null : new HashMap<>(scans);
    return config;
  }

  private static FamilyDiscoveryConfig oneShotScan(String family) {
    FamilyDiscoveryConfig config = new FamilyDiscoveryConfig();
    config.family = family;
    config.generation = Date.from(START_TIME.plusSeconds(30));
    return config;
  }

  private static FamilyDiscoveryConfig periodicScan(String family, int intervalSec) {
    FamilyDiscoveryConfig config = new FamilyDiscoveryConfig();
    config.family = family;
    config.generation = Date.from(START_TIME);
    config.scan_interval_sec = intervalSec;
    return config;
  }

  private static final class ScheduledEntry {

    private final Date time;
    private final Runnable task;
    private final ScheduledFuture<?> future;
    private boolean cancelled;

    ScheduledEntry(Date time, Runnable task) {
      this.time = time;
      this.task = task;
      this.future = mock(ScheduledFuture.class, invocation -> switch (invocation.getMethod()
          .getName()) {
        case "cancel" -> {
          cancelled = true;
          yield true;
        }
        case "isCancelled" -> cancelled;
        default -> null;
      });
    }

    Date time() {
      return time;
    }

    Runnable task() {
      return task;
    }

    boolean isCancelled() {
      return cancelled;
    }

    ScheduledFuture<?> future() {
      return future;
    }
  }

  /**
   * Minimal in-memory manager that records scheduled futures instead of running them.
   */
  private static class FakeDiscoveryManager implements DiscoveryManager {

    final List<ScheduledEntry> futures = Collections.synchronizedList(new ArrayList<>());
    final List<DiscoveryEvents> published = Collections.synchronizedList(new ArrayList<>());
    private final DiscoveryScheduler discoveryScheduler = new DiscoveryScheduler(this);
    private final ReentrantLock hostStateLock = new ReentrantLock();
    private final ManagerHost host;
    Instant now = Instant.now();
    volatile Runnable onPostDiscoveryProcess;
    boolean sawEmptyScansMap;
    boolean sawEmptyFamiliesMap;
    boolean sawActiveBeforeStartMarker;
    boolean throwOnNextUpdateState;
    boolean throwOnNextPublish;
    boolean rejectSchedules;
    int stateUpdates;
    int hostLockEntries;

    FakeDiscoveryManager(Map<String, FamilyProvider> providers) {
      host = mock(ManagerHost.class, invocation -> switch (invocation.getMethod().getName()) {
        case "getLocalnetProvider" -> providers.get((String) invocation.getArgument(0));
        case "getStartTime" -> Date.from(START_TIME);
        case "withStateLock" -> {
          hostStateLock.lock();
          try {
            hostLockEntries++;
            ((Runnable) invocation.getArgument(0)).run();
          } finally {
            hostStateLock.unlock();
          }
          yield null;
        }
        case "tryWithStateLock" -> {
          if (!hostStateLock.tryLock()) {
            yield false;
          }
          try {
            hostLockEntries++;
            ((Runnable) invocation.getArgument(0)).run();
            yield true;
          } finally {
            hostStateLock.unlock();
          }
        }
        case "publish" -> {
          if (throwOnNextPublish) {
            throwOnNextPublish = false;
            throw new RuntimeException("simulated publish failure");
          }
          if (invocation.getArguments().length == 1) {
            published.add(invocation.getArgument(0));
          }
          yield null;
        }
        default -> null;
      });
    }

    void runFuture(int index) {
      ScheduledEntry entry = futures.get(index);
      assertFalse("Scheduled future was cancelled", entry.isCancelled());
      entry.task().run();
      assertFalse("Scheduled future cancelled itself while firing", entry.isCancelled());
    }

    int futureIndexFor(String scanName) {
      Date generation = getDiscoveryState().scans.get(scanName).generation;
      for (int i = 0; i < futures.size(); i++) {
        if (!futures.get(i).isCancelled() && futures.get(i).time().equals(generation)) {
          return i;
        }
      }
      throw new IllegalStateException("No future scheduled for scan " + scanName);
    }

    int futureIndexForFamily(String family) {
      Date generation = getDiscoveryState().families.get(family).generation;
      for (int i = 0; i < futures.size(); i++) {
        if (!futures.get(i).isCancelled() && futures.get(i).time().equals(generation)) {
          return i;
        }
      }
      throw new IllegalStateException("No future scheduled for family " + family);
    }

    @Override
    public DiscoveryScheduler getDiscoveryScheduler() {
      return discoveryScheduler;
    }

    @Override
    public Instant getScanNow() {
      return now;
    }

    @Override
    public ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable) {
      if (rejectSchedules) {
        throw new RejectedExecutionException("executor shut down");
      }
      ScheduledEntry entry = new ScheduledEntry(startGeneration, runnable);
      futures.add(entry);
      return entry.future();
    }

    @Override
    public ManagerHost getHost() {
      return host;
    }

    @Override
    public Map<String, FeatureDiscovery> getFeatures() {
      return Map.of();
    }

    @Override
    public Map<String, PointPointsetModel> enumeratePoints(String deviceId) {
      return Map.of();
    }

    @Override
    public DeviceManager getDeviceManager() {
      return null;
    }

    @Override
    public void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents) {
      Runnable hook = onPostDiscoveryProcess;
      if (hook != null) {
        hook.run();
      }
    }

    @Override
    public void updateState(Object state) {
      if (throwOnNextUpdateState) {
        throwOnNextUpdateState = false;
        throw new RuntimeException("simulated state failure");
      }
      stateUpdates++;
      DiscoveryConfig currentConfig = getDiscoveryConfig();
      if (state instanceof DiscoveryState ds) {
        if (ds.scans != null && ds.scans.isEmpty()
            && (currentConfig == null || currentConfig.scans == null)) {
          sawEmptyScansMap = true;
        }
        if (ds.families != null && ds.families.isEmpty()
            && (currentConfig == null || currentConfig.families == null)) {
          sawEmptyFamiliesMap = true;
        }
        if (ds.scans != null) {
          ds.scans.forEach((name, scanState) -> {
            if (scanState != null && scanState.phase == ACTIVE) {
              boolean hasStartMarker;
              synchronized (published) {
                hasStartMarker = published.stream().anyMatch(ev -> name.equals(ev.scan)
                    && Integer.valueOf(0).equals(ev.event_no)
                    && scanState.generation.toInstant().getEpochSecond()
                        == ev.generation.toInstant().getEpochSecond());
              }
              if (!hasStartMarker) {
                sawActiveBeforeStartMarker = true;
              }
            }
          });
        }
      }
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void error(String message, Throwable e) {
    }

    @Override
    public void updateInterval(Integer sampleRateSec) {
    }

    @Override
    public void periodicUpdate() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public String getDeviceId() {
      return "AHU-1";
    }

    @Override
    public int incrementEventCount() {
      return 0;
    }

    @Override
    public void debug(String message) {
    }

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }
  }
}
