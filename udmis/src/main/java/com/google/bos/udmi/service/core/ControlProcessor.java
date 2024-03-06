package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.CloudQuery;
import udmi.schema.EndpointConfiguration;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

  private TargetProcessor targetProcessor;

  public ControlProcessor(EndpointConfiguration config) {
    super(config);
  }

  public void activate() {
    super.activate();
    targetProcessor = UdmiServicePod.getComponent(TargetProcessor.class);
  }

  /**
   * Handle a cloud query command.
   */
  @DispatchHandler
  public void cloudQueryHandler(CloudQuery query) {
    new CloudQueryHandler(this, targetProcessor).process(query);
  }
}
