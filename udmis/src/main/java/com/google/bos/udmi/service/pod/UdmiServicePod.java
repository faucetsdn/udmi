package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;

import com.google.bos.udmi.service.core.StateHandler;
import com.google.bos.udmi.service.core.TargetHandler;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import udmi.schema.EndpointConfiguration;
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
    try {
      checkState(args.length == 1, "expected exactly one argument: configuration_file");

      podConfiguration = JsonUtil.loadFileRequired(PodConfiguration.class, args[0]);

      targetHandler =
          createComponent(TargetHandler.class, makeConfig(podConfiguration.flows.get("target")));
      stateHandler =
          createComponent(StateHandler.class, makeConfig(podConfiguration.flows.get("state")));
    } catch (Exception e) {
      throw new RuntimeException("While instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  public static void main(String[] args) {
    new UdmiServicePod(args);
  }

  private <T extends UdmisComponent> T createComponent(Class<T> clazz,
      EndpointConfiguration config) {
    return ifNotNullGet(config, () -> UdmisComponent.create(clazz, config));
  }

  private EndpointConfiguration makeConfig(EndpointConfiguration defined) {
    return combineConfig(podConfiguration.flow_defaults, defined);
  }

  public PodConfiguration getPodConfiguration() {
    return podConfiguration;
  }
}
