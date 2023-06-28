package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.JsonUtil.stringify;

import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;

/**
 * Handle and process messages from the "target" message channel (e.g. PubSub topic). Currently,
 * this is just a simple pass-through with no logic or functionality. It's essentially a TAP point
 * for all events flowing through the system.
 */
public class TargetProcessor extends ProcessorBase {

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    // private void sendReflectCommand(Envelope reflection, Envelope message, Object payload) {
    String reflectRegistry = reflection.deviceRegistryId;
    String deviceRegistry = reflection.deviceId;
    Envelope message = new Envelope();
    message.payload = encodeBase64(stringify(defaultedMessage));
    provider.sendCommand(reflectRegistry, deviceRegistry, SubFolder.UDMI, stringify(message));
  }
}
