package com.google.bos.udmi.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  @Test
  void testQueryTimeoutHeadroomForSlowFdatasync() throws Exception {
    KV mockKv = mock(KV.class);
    GetResponse mockResponse = mock(GetResponse.class);
    KeyValue mockKeyValue = mock(KeyValue.class);
    when(mockKeyValue.getValue()).thenReturn(
        ByteSequence.from("2026-10-23T05:04:20Z".getBytes(StandardCharsets.UTF_8)));
    when(mockResponse.getCount()).thenReturn(1L);
    when(mockResponse.getKvs()).thenReturn(List.of(mockKeyValue));

    CompletableFuture<GetResponse> delayedFuture = new CompletableFuture<>() {
      @Override
      public GetResponse get(long timeout, TimeUnit unit) throws TimeoutException {
        if (unit.toSeconds(timeout) < 15) {
          throw new TimeoutException("Simulated 14.6s etcd slow fdatasync stall");
        }
        return mockResponse;
      }
    };
    when(mockKv.get(any(ByteSequence.class))).thenReturn(delayedFuture);

    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);
    Field kvField = EtcdDataProvider.class.getDeclaredField("kvClient");
    kvField.setAccessible(true);
    kvField.set(provider, mockKv);

    assertEquals("2026-10-23T05:04:20Z",
        provider.getEntry("/r/ZZ-TRI-FECTA_1/d/AHU-1:last_state_time"));
  }
}
