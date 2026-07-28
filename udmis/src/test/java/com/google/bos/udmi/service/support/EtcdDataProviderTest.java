package com.google.bos.udmi.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;

class EtcdDataProviderTest {

  @Test
  void testDefaultMaxInboundMessageSize() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);
    assertEquals(Integer.MAX_VALUE, provider.getMaxInboundMessageSize());
  }

  @Test
  void testCustomMaxInboundMessageSize() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false,max_inbound_message_size=10485760";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);
    assertEquals(10485760, provider.getMaxInboundMessageSize());
  }

  @Test
  void testInvalidMaxInboundMessageSize() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false,max_inbound_message_size=not_a_number";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);
    assertEquals(Integer.MAX_VALUE, provider.getMaxInboundMessageSize());
  }
}
