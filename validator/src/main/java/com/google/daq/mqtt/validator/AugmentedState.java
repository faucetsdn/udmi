package com.google.daq.mqtt.validator;

import udmi.schema.State;

/**
 * Special state wrapper class including extra fields for side-band data from backend functions.
 */
public class AugmentedState extends State {
  // Extra field indicating if the last config has been ackd by the device.
  public String configAcked;
}
