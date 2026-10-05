package com.google.bos.udmi.service.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  void testListRegistriesKeyJumpHandlesPrefixCollisionsAndReflect() {
    NavigableMap<String, String> etcdKeys = new TreeMap<>();
    etcdKeys.put(":registries", "US-MTV-1");
    etcdKeys.put("/r/EMPTY-REG:created_at", "2026-08-28T00:00:00Z");
    etcdKeys.put("/r/UDMI-REFLECT/d/US-MTV-1:last_config", "{}");
    etcdKeys.put("/r/US-MTV-1/c/active:dev-1", "2026-08-28T00:00:00Z");
    etcdKeys.put("/r/US-MTV-1/d/dev-1:last_state", "{}");
    etcdKeys.put("/r/US-MTV-1/d/dev-2:last_state", "{}");
    etcdKeys.put("/r/US-MTV-1:created_at", "2026-08-28T00:00:00Z");
    etcdKeys.put("/r/US-MTV-1015/d/dev-1:last_state", "{}");
    etcdKeys.put("/r/US-MTV-1015:created_at", "2026-08-28T00:00:00Z");

    KV mockKv = mock(KV.class);
    when(mockKv.get(any(ByteSequence.class), any(GetOption.class))).thenAnswer(inv -> {
      ByteSequence startSeq = inv.getArgument(0);
      GetOption opt = inv.getArgument(1);
      String startKey = new String(startSeq.getBytes(), StandardCharsets.UTF_8);
      String endKey = new String(opt.getEndKey().get().getBytes(), StandardCharsets.UTF_8);
      String match = etcdKeys.subMap(startKey, true, endKey, false).keySet().stream()
          .findFirst()
          .orElse(null);
      GetResponse response = mock(GetResponse.class);
      if (match == null) {
        when(response.getKvs()).thenReturn(List.of());
      } else {
        KeyValue kv = mock(KeyValue.class);
        when(kv.getKey()).thenReturn(
            ByteSequence.from(match.getBytes(StandardCharsets.UTF_8)));
        when(response.getKvs()).thenReturn(List.of(kv));
      }
      return completedFuture(response);
    });

    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess, mockKv);

    Set<String> registries = provider.listRegistries();
    assertEquals(Set.of("EMPTY-REG", "US-MTV-1", "US-MTV-1015"), registries);

    ArgumentCaptor<ByteSequence> keyCaptor = ArgumentCaptor.forClass(ByteSequence.class);
    verify(mockKv, times(7)).get(keyCaptor.capture(), any(GetOption.class));
    List<String> queriedKeys = keyCaptor.getAllValues().stream()
        .map(bs -> new String(bs.getBytes(), StandardCharsets.UTF_8))
        .toList();
    assertEquals(
        List.of(
            "/r/",
            "/r/EMPTY-REG;",
            "/r/UDMI-REFLECT0",
            "/r/US-MTV-10",
            "/r/US-MTV-10150",
            "/r/US-MTV-1015;",
            "/r/US-MTV-1;"),
        queriedKeys);
  }
}
