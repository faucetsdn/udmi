package com.google.bos.udmi.service.core;

/**
 * Handle and process messages from the "target" message channel (e.g. PubSub topic). Currently,
 * this is just a simple pass-through with no logic or functionality. It's essentially a TAP point
 * for all events flowing through the system.
 */
public class TargetHandler extends UdmisComponent {

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    publish(defaultedMessage);
  }
}
