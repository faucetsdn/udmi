package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.toDate;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
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

  private CloudModel makeCloudModel(String registryId) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.last_event_time = toDate(targetProcessor.getLastSeen(registryId));
    return cloudModel;
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

    if (envelope.deviceRegistryId == null) {
      processListRegistries(envelope, query);
    } else if (envelope.deviceId == null) {
      processListDevices(envelope, query);
    } else {
      processDetailDevice(envelope, query);
    }
  }

  private void processDetailDevice(Envelope envelope, CloudQuery query) {
    debug("Detailing device %s/%s", envelope.deviceRegistryId, envelope.deviceId);
  }

  private void processListDevices(Envelope envelope, CloudQuery query) {
    debug("Listing devices for %s", envelope.deviceRegistryId);
    CloudModel cloudModel = iotAccess.listDevices(envelope.deviceRegistryId);
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.devices = cloudModel.device_ids.entrySet().stream().collect(Collectors.toMap(
        Entry::getKey, entry -> convertDeviceEntry(entry.getValue())));
    publish(discoveryEvent);
  }

  @NotNull
  private CloudModel convertDeviceEntry(CloudModel entry) {
    return entry;
  }

  private void processListRegistries(Envelope envelope, CloudQuery query) {
    Set<String> registries = iotAccess.listRegistries();
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.registries = registries.stream()
        .collect(Collectors.toMap(registryId -> registryId, this::makeCloudModel));
    publish(discoveryEvent);

    List<String> active = discoveryEvent.registries.entrySet().stream()
        .filter(entry -> entry.getValue().last_event_time != null).map(Entry::getKey).toList();

    debug("Query resulted in %d registries (%d active)", registries.size(), active.size());

    active.forEach(id -> issueRegistryQuery(envelope, query, id));
  }

  private void issueRegistryQuery(Envelope envelope, CloudQuery origin, String registryId) {
    CloudQuery cloudQuery = new CloudQuery();
    cloudQuery.generation = origin.generation;
    envelope.deviceRegistryId = registryId;
    sideProcess(envelope, cloudQuery);
  }
}
