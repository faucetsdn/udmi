package com.google.daq.mqtt.validator;

import udmi.schema.State;

public class AugmentedState extends State {
  // Extra field indicating if the last config has been ackd by the device.
  public String configAcked;
}
