package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudQuery;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.RegistryDiscovery;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

  public static final String IOT_SCAN_FAMILY = "iot";

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

  private RegistryDiscovery makeRegistryDiscovery(String registryId) {
    return new RegistryDiscovery();
  }

  @DispatchHandler
  public void cloudQueryHandler(CloudQuery query) {
    Set<String> registries = iotAccess.listRegistries();
    debug("Registry query resulted in " + registries.size());
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = IOT_SCAN_FAMILY;
    discoveryEvent.generation = query.generation;
    discoveryEvent.registries = registries.stream()
        .collect(Collectors.toMap(registryId -> registryId, this::makeRegistryDiscovery));
    publish(discoveryEvent);
  }
}
