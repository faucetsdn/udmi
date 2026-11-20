package daq.pubber.impl.provider;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static udmi.lib.ProtocolFamily.BACNET;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Provides for the bacnet family of stuffs.
 */
public class PubberBacnetProvider extends PubberProviderBase implements PubberFamilyProvider {

  private static final int BACNET_DISCOVERY_RATE_SEC = 1;

  private record ActiveScan(Deque<String> toReport, Consumer<String> publisher) {
  }

  private volatile ActiveScan activeScan;

  /**
   * Provider for metadata-based (simulated) bacnet discovery.
   */
  public PubberBacnetProvider(ManagerHost host, String family, String deviceId) {
    super(host, family, deviceId);
    checkState(family.equals(BACNET));
  }

  @Override
  public void periodicUpdate() {
    ifNotNullThen(activeScan,
        scan -> ifNotNullThen(scan.toReport().poll(), scan.publisher()));
  }

  @Override
  public void startScan(FamilyDiscoveryConfig discoveryConfig,
      BiConsumer<String, DiscoveryEvents> publisher) {
    super.startScan(discoveryConfig, publisher);
    activeScan = new ActiveScan(
        new ConcurrentLinkedDeque<>(getAllDevices().keySet()), getResultPublisher());
    updateInterval(BACNET_DISCOVERY_RATE_SEC);
  }

  @Override
  public void stopScan() {
    activeScan = null;
    super.stop();
  }
}
