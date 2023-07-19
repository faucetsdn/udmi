package com.google.bos.udmi.service.messaging;

import udmi.schema.State;

/**
 * Empty wrapper class to have a type name of StateUpdate that just simply implements the State type
 * that otherwise would have no subFolder associated with it.
 */
public class StateUpdate extends State {

  /**
   * Special internal value used to check if a config-to-device was properly acknowledged by
   * the device (relates to MQTT QOS settings).
   */
  public Boolean configAcked;

}
