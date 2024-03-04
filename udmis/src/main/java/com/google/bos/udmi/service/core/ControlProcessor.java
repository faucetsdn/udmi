package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import udmi.schema.CloudQuery;
import udmi.schema.EndpointConfiguration;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

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

  @DispatchHandler
  public void cloudQueryHandler(CloudQuery query) {
    iotAccess.fetchRe
    Set<String> registriesForRegion = iotAccess.getRegistriesForRegion(null);
    Set<Object> registries = registriesForRegion.stream().map(iotAccess::getRegistriesForRegion)
        .collect(HashSet::new, Set::addAll, Set::addAll);
    debug("Fetched " + registries.size());
  }

}
