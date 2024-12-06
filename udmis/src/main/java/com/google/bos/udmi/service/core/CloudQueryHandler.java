package com.google.bos.udmi.service.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.requireNull;
import static com.google.udmi.util.GeneralUtils.toDate;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessBase;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope;

/**
 * Container for handling CloudQuery messages.
 */
public class CloudQueryHandler {

  private final ControlProcessor controller;
  private final IotAccessBase iotAccess;
  private final TargetProcessor target;
  private final CloudQuery query;
  private final Envelope envelope;
  private final String savedEnvelope;
  private final String savedQuery;

  /**
   * Create a query handler for cloud queries.
   */
  public CloudQueryHandler(ControlProcessor controlProcessor, CloudQuery cloudQuery) {
    controller = controlProcessor;
    iotAccess = controller.iotAccess;
    target = controller.targetProcessor;
    query = cloudQuery;
    envelope = controller.getContinuation(cloudQuery).getEnvelope();
    debug("Starting CloudQuery for %s/%s %s", envelope.deviceRegistryId, envelope.deviceId,
        envelope.transactionId);
    savedQuery = stringifyTerse(query);
    savedEnvelope = stringifyTerse(envelope);
  }

  public static void processQuery(ControlProcessor controlProcessor, CloudQuery query) {
    new CloudQueryHandler(controlProcessor, query).process();
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
    Envelope mutated = deepCopy(envelope);
    mutator.accept(mutated);
    controller.sideProcess(mutated, query);
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

    debug("Project has %d registries", registries.size());

    ifTrueThen(shouldTraverseRegistries(), () -> registries.forEach(this::issueModifiedRegistry));
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

    CloudModel cloudModel = iotAccess.listDevices(deviceRegistryId, null);
    Set<Entry<String, CloudModel>> deviceSet = new HashSet<>(cloudModel.device_ids.entrySet());
    debug("Queried registry %s for %d totaling %d %s",
        envelope.deviceRegistryId, cloudModel.device_ids.size(), deviceSet.size(),
        envelope.transactionId);

    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.scan_family = ProtocolFamily.IOT;
    discoveryEvent.generation = query.generation;
    discoveryEvent.devices = deviceSet.stream().collect(Collectors.toMap(
        Entry::getKey, entry -> convertDeviceEntry(entry.getValue())));
    publish(discoveryEvent);

    List<String> active = discoveryEvent.devices.entrySet().stream()
        .filter(entry -> !isTrue(entry.getValue().blocked)).map(Entry::getKey).toList();

    debug("Listed registry %s with %d devices (%d active)", deviceRegistryId,
        discoveryEvent.devices.size(), active.size());

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
  public synchronized void process() {
    if (envelope.deviceRegistryId == null) {
      queryAllRegistries();
    } else if (envelope.deviceId == null) {
      queryRegistryDevices();
    } else {
      queryDeviceDetails();
    }
    checkState(savedEnvelope.equals(stringifyTerse(envelope)), "mutated envelope");
    checkState(savedQuery.equals(stringifyTerse(query)), "mutated query");
  }
}
