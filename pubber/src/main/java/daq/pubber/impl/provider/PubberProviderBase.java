package daq.pubber.impl.provider;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import udmi.lib.base.ManagerBase;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Base class for functionality supporting all pubber discovery providers.
 */
public class PubberProviderBase extends ManagerBase {

  private final String family;

  public PubberProviderBase(ManagerHost host, String family, String deviceId) {
    super(host, deviceId);
    this.family = family;
  }

  /**
   * Get a ref value that describes a point for self enumeration.
   */
  private RefDiscovery getModelPointRef(Entry<String, PointPointsetModel> entry) {
    RefDiscovery refDiscovery = new RefDiscovery();
    PointPointsetModel model = entry.getValue();
    refDiscovery.writable = model.writable;
    refDiscovery.units = model.units;
    refDiscovery.point = entry.getKey();
    return refDiscovery;
  }

  protected DiscoveryEvents augmentSend(Entry<String, Metadata> entry, boolean enumerate) {
    String addr = catchToNull(() -> entry.getValue().localnet.families.get(family).addr);
    DiscoveryEvents event = new DiscoveryEvents();
    event.addr = addr;
    event.refs = ifTrueGet(enumerate, () -> getDiscoveredRefs(entry.getValue()));
    return event;
  }

  private Map<String, RefDiscovery> getDiscoveredRefs(Metadata entry) {
    Map<String, PointPointsetModel> points = catchToNull(() -> entry.pointset.points);
    return ofNullable(points).orElse(ImmutableMap.of()).entrySet().stream()
        .filter(point -> nonNull(point.getValue().ref))
        .map(this::pointsetToRef)
        .collect(toMap(Entry::getKey, Entry::getValue));
  }

  protected void maybeSendResult(Entry<String, Metadata> metadataEntry,
      BiConsumer<String, DiscoveryEvents> publisher, boolean enumerate) {
    publisher.accept(metadataEntry.getKey(), augmentSend(metadataEntry, enumerate));
  }

  private Entry<String, RefDiscovery> pointsetToRef(Entry<String, PointPointsetModel> e) {
    return new SimpleEntry<>(e.getValue().ref, getModelPointRef(e));
  }
}
