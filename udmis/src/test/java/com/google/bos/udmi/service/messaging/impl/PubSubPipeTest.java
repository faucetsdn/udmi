package com.google.bos.udmi.service.messaging.impl;

import com.google.common.base.Strings;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;

/**
 * Tests for PubSub message pipe.
 */
public class PubSubPipeTest extends MessagePipeTestBase {

  @Override
  protected void augmentConfig(EndpointConfiguration configuration, boolean reversed) {
    configuration.protocol = Protocol.PUBSUB;
  }

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(PubSubPipe.EMULATOR_HOST);
    if (!environmentEnabled) {
      debug(
          "Skipping test because no emulator defined in " + PubSubPipe.EMULATOR_HOST_ENV);
    }
    return environmentEnabled;
  }
}