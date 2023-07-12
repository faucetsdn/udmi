package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Config;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;

/**
 * Core UDMIS function to process raw State messages from devices and normalize them for the rest of
 * the system. Involves tagging the envelope with the appropriate designators, and splitting up the
 * monolithic block into constituent parts.
 */
public class ConfigProcessor extends ProcessorBase {

  private static final Set<String> CONFIG_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());
  public static final String IOT_ACCESS_COMPONENT = "iot_access";

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
  }

  @Override
  protected void registerHandlers() {
    registerHandler(Config.class, this::configHandler);
  }

  private void configHandler(Config message) {
    MessageContinuation continuation = getContinuation(message);
  }

}
