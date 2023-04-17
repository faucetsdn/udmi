package com.google.bos.udmi.service.pod;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.bos.udmi.service.core.StateHandler;
import com.google.bos.udmi.service.core.TargetHandler;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import udmi.schema.MessageConfiguration;
import udmi.schema.PodConfiguration;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod {

  private final PodConfiguration podConfiguration;
  private final StateHandler stateHandler;
  private final TargetHandler targetHandler;

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    checkState(args.length == 1, "expected exactly one argument: configuration");

    podConfiguration = JsonUtil.loadFileRequired(PodConfiguration.class, args[0]);

    stateHandler = createComponent(StateHandler.class, podConfiguration.state_flow);
    targetHandler = createComponent(TargetHandler.class, podConfiguration.target_flow);
  }

  private <T extends UdmisComponent> T createComponent(Class<T> clazz, MessageConfiguration config) {
    return ifNotNullGet(config, () -> UdmisComponent.create(clazz, config));
  }

  public static void main(String[] args) {
    new UdmiServicePod(args);
  }
}
