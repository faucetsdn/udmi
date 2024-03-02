package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import java.util.Objects;
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
}
