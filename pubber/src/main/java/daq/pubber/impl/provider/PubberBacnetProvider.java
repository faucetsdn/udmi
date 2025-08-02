package daq.pubber.impl.provider;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static udmi.lib.ProtocolFamily.BACNET;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryConfig;

/**
 * Provides for the bacnet family of stuffs.
 */
public class PubberBacnetProvider extends PubberProviderBase implements PubberFamilyProvider {

  private static final int BACNET_DISCOVERY_RATE_SEC = 1;
  private final Deque<String> toReport = new ArrayDeque<>();

  /**
   * Provider for metadata-based (simulated) bacnet discovery.
   */
  public PubberBacnetProvider(ManagerHost host, String family, String deviceId) {
    super(host, family, deviceId);
    checkState(family.equals(BACNET));
  }

  @Override
  public synchronized void periodicUpdate() {
    ifNotNullThen(toReport.poll(), getResultPublisher());
  }

  @Override
  public synchronized void startScan(FamilyDiscoveryConfig discoveryConfig,
      BiConsumer<String, DiscoveryEvents> publisher) {
    super.startScan(discoveryConfig, publisher);
    toReport.clear();
    toReport.addAll(getAllDevices().keySet());
    updateInterval(BACNET_DISCOVERY_RATE_SEC);
  }

  @Override
  public synchronized void stopScan() {
    super.stop();
  }
}
