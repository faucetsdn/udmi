package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.toDate;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
import udmi.schema.CloudQuery.Depth;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

  public static final String IOT_SCAN_FAMILY = "iot";
  private TargetProcessor targetProcessor;

  public ControlProcessor(EndpointConfiguration config) {
    super(config);
  }

  private static String makeTransactionId() {
    return format("CP:%08x", Objects.hash(System.currentTimeMillis(), Thread.currentThread()));
  }

  @Override
  protected void defaultHandler(Object message) {
    debug("Received defaulted control message type %s: %s", message.getClass().getSimpleName(),
        stringifyTerse(message));
  }

  @NotNull
  private CloudModel convertDeviceEntry(CloudModel entry) {
    return entry;
  }

  private void issueModifiedQuery(Envelope envelope, CloudQuery query, Consumer<Envelope> mutator) {
    CloudQuery cloudQuery = new CloudQuery();
    cloudQuery.generation = query.generation;
    mutator.accept(envelope);
    sideProcess(envelope, cloudQuery);
  }

  private CloudModel makeCloudModel(String registryId) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.last_event_time = toDate(targetProcessor.getLastSeen(registryId));
    cloudModel.credentials = null;
    return cloudModel;
  }

  private void queryAllRegistries(Envelope envelope, CloudQuery query) {
    Set<String> registries = iotAccess.listRegistries();
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.registries = registries.stream()
        .collect(Collectors.toMap(registryId -> registryId, this::makeCloudModel));

    publish(discoveryEvent);

    List<String> active = discoveryEvent.registries.entrySet().stream()
        .filter(entry -> entry.getValue().last_event_time != null).map(Entry::getKey).toList();

    debug("Found %d registries (%d active)", registries.size(), active.size());

    if (shouldTraverseRegistries(query)) {
      active.forEach(id -> issueModifiedQuery(envelope, query, e -> e.deviceRegistryId = id));
    }
  }

  private boolean shouldTraverseRegistries(CloudQuery query) {
    return (Depth.DEVICES == query.depth) || shouldDetailDevices(query);
  }

  private boolean shouldDetailDevices(CloudQuery query) {
    return Depth.DETAILS == query.depth;
  }

  private void queryDeviceDetails(Envelope envelope, CloudQuery query) {
    String deviceRegistryId = requireNonNull(envelope.deviceRegistryId, "registry id");
    String deviceId = requireNonNull(envelope.deviceId, "device id");

    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.cloud_model = iotAccess.fetchDevice(deviceRegistryId, deviceId);
    discoveryEvent.cloud_model.operation = null;

    debug("Detailed device %s/%s", deviceRegistryId, deviceId);

    publish(discoveryEvent);
  }

  private void queryRegistryDevices(Envelope envelope, CloudQuery query) {
    String deviceRegistryId = requireNonNull(envelope.deviceRegistryId, "registry id");
    CloudModel cloudModel = iotAccess.listDevices(deviceRegistryId);

    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.devices = cloudModel.device_ids.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey, entry -> convertDeviceEntry(entry.getValue())));
    publish(discoveryEvent);

    List<String> active = discoveryEvent.devices.entrySet().stream()
        .filter(entry -> !isTrue(entry.getValue().blocked)).map(Entry::getKey).toList();

    debug("Registry %s had %d devices (%d active)", deviceRegistryId, discoveryEvent.devices.size(),
        active.size());

    if (shouldDetailDevices(query)) {
      active.forEach(id -> issueModifiedQuery(envelope, query, e -> e.deviceId = id));
    }
  }

  @Override
  public void activate() {
    super.activate();
    targetProcessor = UdmiServicePod.getComponent(TargetProcessor.class);
  }

  /**
   * Handle a cloud query command.
   */
  @DispatchHandler
  public void cloudQueryHandler(CloudQuery query) {
    Envelope envelope = getContinuation(query).getEnvelope();

    // If the depth is not defined, recurse down one level.
    if (envelope.deviceRegistryId == null) {
      ifNullThen(query.depth, () -> query.depth = Depth.DEVICES);
      queryAllRegistries(envelope, query);
    } else if (envelope.deviceId == null) {
      ifNullThen(query.depth, () -> query.depth = Depth.DETAILS);
      queryRegistryDevices(envelope, query);
    } else {
      queryDeviceDetails(envelope, query);
    }
  }
}
