package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.toDate;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessBase;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
import udmi.schema.CloudQuery.Depth;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;

/**
 * Container for handling CloudQuery messages.
 */
public class CloudQueryHandler {

  private final ControlProcessor controller;
  private final IotAccessBase iotAccess;
  private final TargetProcessor target;
  private CloudQuery query;
  private Envelope envelope;

  /**
   * Create a query handler for cloud queries.
   */
  public CloudQueryHandler(ControlProcessor controlProcessor) {
    controller = controlProcessor;
    iotAccess = controller.iotAccess;
    target = controller.targetProcessor;
  }

  @NotNull
  private CloudModel convertDeviceEntry(CloudModel entry) {
    return entry;
  }

  private void debug(String format, Object... args) {
    controller.debug(format, args);
  }

  private void issueModifiedQuery(Consumer<Envelope> mutator) {
    CloudQuery cloudQuery = new CloudQuery();
    cloudQuery.generation = query.generation;
    mutator.accept(envelope);
    controller.sideProcess(envelope, cloudQuery);
  }

  private void issueModifiedRegistry(String registryId) {
    issueModifiedQuery(e -> e.deviceRegistryId = registryId);
  }

  private void issueModifiedDevice(String id) {
    issueModifiedQuery(e -> e.deviceId = id);
  }

  private CloudModel makeCloudModel(String registryId) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.last_event_time = toDate(target.getLastSeen(registryId));
    cloudModel.credentials = null;
    return cloudModel;
  }

  private void publish(DiscoveryEvent discoveryEvent) {
    controller.publish(discoveryEvent);
  }

  private void queryAllRegistries() {
    Set<String> registries = iotAccess.listRegistries();
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.registries = registries.stream()
        .collect(Collectors.toMap(registryId -> registryId, this::makeCloudModel));

    publish(discoveryEvent);

    List<String> active = discoveryEvent.registries.entrySet().stream()
        .filter(entry -> entry.getValue().last_event_time != null).map(Entry::getKey).toList();

    debug("Found %d registries (%d active)", registries.size(), active.size());

    ifTrueThen(shouldTraverseRegistries(), () -> active.forEach(this::issueModifiedRegistry));
  }

  private void queryDeviceDetails() {
    String deviceRegistryId = requireNonNull(envelope.deviceRegistryId, "registry id");
    String deviceId = requireNonNull(envelope.deviceId, "device id");

    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.cloud_model = iotAccess.fetchDevice(deviceRegistryId, deviceId);
    discoveryEvent.cloud_model.operation = null;

    debug("Detailed device %s/%s", deviceRegistryId, deviceId);

    publish(discoveryEvent);
  }

  private void queryRegistryDevices() {
    String deviceRegistryId = requireNonNull(envelope.deviceRegistryId, "registry id");
    CloudModel cloudModel = iotAccess.listDevices(deviceRegistryId);

    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.devices = cloudModel.device_ids.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey, entry -> convertDeviceEntry(entry.getValue())));
    publish(discoveryEvent);

    List<String> active = discoveryEvent.devices.entrySet().stream()
        .filter(entry -> !isTrue(entry.getValue().blocked)).map(Entry::getKey).toList();

    debug("Registry %s had %d devices (%d active)", deviceRegistryId, discoveryEvent.devices.size(),
        active.size());

    ifTrueThen(shouldDetailDevices(), () -> active.forEach(this::issueModifiedDevice));
  }

  private boolean shouldDetailDevices() {
    return Depth.DETAILS == query.depth;
  }

  private boolean shouldTraverseRegistries() {
    return (Depth.DEVICES == query.depth) || shouldDetailDevices();
  }

  /**
   * Process an individual cloud query.
   */
  public synchronized void process(CloudQuery newQuery) {
    query = newQuery;
    envelope = controller.getContinuation(newQuery).getEnvelope();

    // If the query.depth is not defined then default to recursing down one level.
    if (envelope.deviceRegistryId == null) {
      ifNullThen(newQuery.depth, () -> newQuery.depth = Depth.DEVICES);
      queryAllRegistries();
    } else if (envelope.deviceId == null) {
      ifNullThen(newQuery.depth, () -> newQuery.depth = Depth.DETAILS);
      queryRegistryDevices();
    } else {
      queryDeviceDetails();
    }
    query = null;
  }
}
