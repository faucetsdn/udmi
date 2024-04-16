package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.requireNull;
import static com.google.udmi.util.GeneralUtils.toDate;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.udmi.util.GeneralUtils;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.CloudQuery;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.Depths.Depth;
import udmi.schema.DiscoveryEvents;
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

  private void issueModifiedDevice(String deviceId) {
    requireNonNull(deviceId, "device id");
    issueModifiedQuery(e -> {
      e.deviceId = deviceId;
      e.transactionId = e.transactionId + "/d/" + deviceId;
    });
  }

  private void issueModifiedQuery(Consumer<Envelope> mutator) {
    CloudQuery cloudQuery = new CloudQuery();
    cloudQuery.generation = query.generation;
    mutator.accept(envelope);
    controller.sideProcess(envelope, cloudQuery);
  }

  private void issueModifiedRegistry(String registryId) {
    requireNonNull(registryId, "registry id");
    issueModifiedQuery(e -> {
      e.deviceRegistryId = registryId;
      e.transactionId = e.transactionId + "/r/" + registryId;
    });
  }

  private CloudModel makeCloudModel(String registryId) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.last_event_time = toDate(target.getLastSeen(registryId));
    cloudModel.credentials = null;
    return cloudModel;
  }

  private void publish(DiscoveryEvents discoveryEvent) {
    controller.publish(discoveryEvent);
  }

  private void queryAllRegistries() {
    requireNull(envelope.deviceRegistryId, "registry id");
    requireNull(envelope.deviceId, "device id");
    Set<String> registries = iotAccess.getRegistries();
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
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

    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.cloud_model = iotAccess.fetchDevice(deviceRegistryId, deviceId);
    discoveryEvent.cloud_model.operation = null;

    debug("Detailed device %s/%s", deviceRegistryId, deviceId);

    publish(discoveryEvent);
  }

  private void queryRegistryDevices() {
    String deviceRegistryId = requireNonNull(envelope.deviceRegistryId, "registry id");
    requireNull(envelope.deviceId, "device id");

    CloudModel cloudModel = iotAccess.listDevices(deviceRegistryId);

    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.devices = cloudModel.device_ids.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey, entry -> convertDeviceEntry(entry.getValue())));
    publish(discoveryEvent);

    List<String> active = discoveryEvent.devices.entrySet().stream()
        .filter(entry -> !isTrue(entry.getValue().blocked)).map(Entry::getKey).toList();

    debug("Registry %s had %d devices (%d active)", deviceRegistryId, discoveryEvent.devices.size(),
        active.size());

    ifTrueThen(shouldDetailEntries(), () -> active.forEach(this::issueModifiedDevice));
  }

  private boolean shouldDetailEntries() {
    return Depth.DETAILS == query.depth;
  }

  private boolean shouldTraverseRegistries() {
    return (Depth.ENTRIES == query.depth) || shouldDetailEntries();
  }

  /**
   * Process an individual cloud query.
   */
  public synchronized void process(CloudQuery newQuery) {
    query = newQuery;
    envelope = controller.getContinuation(newQuery).getEnvelope();

    if (envelope.deviceRegistryId == null) {
      queryAllRegistries();
    } else if (envelope.deviceId == null) {
      queryRegistryDevices();
    } else {
      queryDeviceDetails();
    }
    query = null;
  }
}
