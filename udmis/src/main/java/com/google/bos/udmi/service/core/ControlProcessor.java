package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.CloudQuery;
import udmi.schema.EndpointConfiguration;

/**
 * Handle the control processor stream for UDMI utility tool clients.
 */
@ComponentName("control")
public class ControlProcessor extends ProcessorBase {

  TargetProcessor targetProcessor;
  private CloudQueryHandler cloudQueryHandler;

  public ControlProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  public void activate() {
    super.activate();
    targetProcessor = UdmiServicePod.getComponent(TargetProcessor.class);
    cloudQueryHandler = new CloudQueryHandler(this);
  }

  /**
   * Handle a cloud query command.
   */
  @DispatchHandler
  public void cloudQueryHandler(CloudQuery query) {
    cloudQueryHandler.process(query);
  }
}
