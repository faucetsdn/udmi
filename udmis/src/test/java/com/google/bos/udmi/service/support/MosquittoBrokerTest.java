package com.google.bos.udmi.service.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;

class MosquittoBrokerTest {

  @Test
  void testBrokerAuthFalseNoDynamicSecurityRequests() {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.hostname = "invalid.localhost.nonexistent";
    endpoint.port = 1883;

    // With brokerAuth=false, no connection to invalid broker should be attempted
    MosquittoBroker broker = new MosquittoBroker(null, endpoint, true, false);

    assertDoesNotThrow(() -> broker.authorize("/r/reg/d/dev", "secret").join());
    assertDoesNotThrow(() -> broker.bindGateway("/r/reg/d/gw", "/r/reg/d/dev").join());
    assertDoesNotThrow(() -> broker.unbindGateway("/r/reg/d/gw", "/r/reg/d/dev").join());

    broker.shutdown();
  }
}
