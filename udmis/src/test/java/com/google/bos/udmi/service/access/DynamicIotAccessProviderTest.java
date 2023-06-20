package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGION;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudModel;
import udmi.schema.IotAccess;

class DynamicIotAccessProviderTest {

  private static final String GCP_REGISTRY = "TEST_GCP";
  private static final String CB_REGISTRY = "TEST_CB";

  @Test
  void modifyConfig() {
    DynamicIotAccessProvider provider = getProvider();
    CloudModel cloudModel = provider.listDevices(GCP_REGISTRY);
  }

  private DynamicIotAccessProvider getProvider() {
    return new DynamicIotAccessProvider();
  }
}