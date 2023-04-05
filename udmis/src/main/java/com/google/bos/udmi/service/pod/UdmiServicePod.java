package com.google.bos.udmi.service.pod;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;

import com.google.bos.udmi.service.core.StateHandler;
import com.google.udmi.util.JsonUtil;
import udmi.schema.PodConfiguration;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod {

  private final PodConfiguration podConfiguration;

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    checkState(args.length == 1, "expected exactly one argument: configuration");

    podConfiguration = JsonUtil.loadFileRequired(PodConfiguration.class, args[0]);

    ifNotNullGet(podConfiguration.udmis_flow, StateHandler::forConfig).activate();
  }

  public static void main(String[] args) {
    new UdmiServicePod(args);
  }
}
