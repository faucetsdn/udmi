package com.google.bos.udmi.service.support;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseRevokeResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.lock.UnlockResponse;
import io.etcd.jetcd.options.GetOption;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  @Test
  void testLockLeaseAndOperationsUseQueryTimeout() throws Exception {
    Client mockClient = mock(Client.class);
    Lease mockLease = mock(Lease.class);
    Lock mockLock = mock(Lock.class);
    when(mockClient.getLeaseClient()).thenReturn(mockLease);
    when(mockClient.getLockClient()).thenReturn(mockLock);

    LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
    when(grantResponse.getID()).thenReturn(42L);
    CompletableFuture<LeaseGrantResponse> grantFuture = new CompletableFuture<>() {
      @Override
      public LeaseGrantResponse get(long timeout, TimeUnit unit) throws TimeoutException {
        if (unit.toSeconds(timeout) < 15) {
          throw new TimeoutException("Grant timeout too short");
        }
        return grantResponse;
      }
    };
    when(mockLease.grant(eq(30L))).thenReturn(grantFuture);

    ByteSequence lockKey = ByteSequence.from("lock-key".getBytes(StandardCharsets.UTF_8));
    LockResponse lockResponse = mock(LockResponse.class);
    when(lockResponse.getKey()).thenReturn(lockKey);
    CompletableFuture<LockResponse> lockFuture = new CompletableFuture<>() {
      @Override
      public LockResponse get(long timeout, TimeUnit unit) throws TimeoutException {
        if (unit.toSeconds(timeout) < 15) {
          throw new TimeoutException("Lock timeout too short");
        }
        return lockResponse;
      }
    };
    when(mockLock.lock(any(ByteSequence.class), eq(42L))).thenReturn(lockFuture);

    UnlockResponse unlockResponse = mock(UnlockResponse.class);
    CompletableFuture<UnlockResponse> unlockFuture = new CompletableFuture<>() {
      @Override
      public UnlockResponse get(long timeout, TimeUnit unit) throws TimeoutException {
        if (unit.toSeconds(timeout) < 15) {
          throw new TimeoutException("Unlock timeout too short");
        }
        return unlockResponse;
      }
    };
    when(mockLock.unlock(eq(lockKey))).thenReturn(unlockFuture);

    LeaseRevokeResponse revokeResponse = mock(LeaseRevokeResponse.class);
    CompletableFuture<LeaseRevokeResponse> revokeFuture = new CompletableFuture<>() {
      @Override
      public LeaseRevokeResponse get(long timeout, TimeUnit unit) throws TimeoutException {
        if (unit.toSeconds(timeout) < 15) {
          throw new TimeoutException("Revoke timeout too short");
        }
        return revokeResponse;
      }
    };
    when(mockLease.revoke(eq(42L))).thenReturn(revokeFuture);

    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotProvider.ETCD;
    iotAccess.options = "enabled=false";
    EtcdDataProvider provider = new EtcdDataProvider(iotAccess);

    Field clientField = EtcdDataProvider.class.getDeclaredField("client");
    clientField.setAccessible(true);
    clientField.set(provider, mockClient);

    Field lockField = EtcdDataProvider.class.getDeclaredField("lockClient");
    lockField.setAccessible(true);
    lockField.set(provider, mockLock);

    try (AutoCloseable ignored = provider.ref().registry("REG-1").device("DEV-1").lock()) {
      verify(mockLease).grant(30L);
    }
    verify(mockLock).unlock(lockKey);
    verify(mockLease).revoke(42L);
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
