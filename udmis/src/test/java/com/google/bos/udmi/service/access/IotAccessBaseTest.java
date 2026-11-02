package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.IotAccess;

class IotAccessBaseTest {

  @Test
  public void emptyOptions() {
    IotAccess access = new IotAccess();
    LocalIotAccessProvider localIotAccessProvider = new LocalIotAccessProvider(access);
    assertEquals(ImmutableMap.of(), localIotAccessProvider.options, "empty options");
  }

  @Test
  public void optionsParser() {
    IotAccess access = new IotAccess();
    access.options = "enable, foo=bar, x=";
    LocalIotAccessProvider localIotAccessProvider = new LocalIotAccessProvider(access);
    ImmutableMap<String, String> expected =
        ImmutableMap.of("enable", "true", "foo", "bar", "x", "");
    assertEquals(expected, localIotAccessProvider.options, "parsed options object");
  }

  @Test
  public void concurrentModifyConfigSerializesPerDevice() throws Exception {
    IotAccess access = new IotAccess();
    AtomicReference<Entry<Long, String>> storedConfig =
        new AtomicReference<>(new SimpleEntry<>(1L, ""));
    AtomicInteger casFailures = new AtomicInteger();

    LocalIotAccessProvider provider = new LocalIotAccessProvider(access) {
      @Override
      public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
        try {
          Thread.sleep(20);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return storedConfig.get();
      }

      @Override
      public String updateConfig(Envelope envelope, String config, Long version) {
        Entry<Long, String> current = storedConfig.get();
        if (!current.getKey().equals(version)
            || !storedConfig.compareAndSet(current, new SimpleEntry<>(version + 1, config))) {
          casFailures.incrementAndGet();
          throw new ConcurrentModificationException("CAS conflict at version " + version);
        }
        return config;
      }
    };

    int threadCount = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      futures.add(executor.submit(() -> {
        startLatch.await(5, TimeUnit.SECONDS);
        Envelope env = new Envelope();
        env.deviceRegistryId = "ZZ-TRI-FECTA";
        env.deviceId = "AHU-1";
        return provider.modifyConfig(env, prev -> prev.getValue() + "[" + idx + "]");
      }));
    }

    startLatch.countDown();
    for (Future<String> future : futures) {
      future.get(10, TimeUnit.SECONDS);
    }
    executor.shutdownNow();

    assertEquals(0, casFailures.get(), "Expected zero CAS conflicts for same-device updates");
    assertEquals(1L + threadCount, storedConfig.get().getKey(), "Final config version");
  }

  @Test
  public void idleDeviceConfigLocksAreEvictedOnGc() throws Exception {
    IotAccess access = new IotAccess();
    LocalIotAccessProvider provider = new LocalIotAccessProvider(access) {
      @Override
      public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
        return new SimpleEntry<>(1L, "{}");
      }

      @Override
      public String updateConfig(Envelope envelope, String config, Long version) {
        return config;
      }
    };

    for (int i = 0; i < 10; i++) {
      Envelope env = new Envelope();
      env.deviceRegistryId = "ZZ-TRI-FECTA";
      env.deviceId = "EPHEMERAL-" + i;
      provider.modifyConfig(env, prev -> prev.getValue());
    }

    for (int attempt = 0; attempt < 10
        && provider.configLocks.values().iterator().hasNext(); attempt++) {
      System.gc();
      Thread.sleep(20);
    }

    assertEquals(false, provider.configLocks.values().iterator().hasNext(),
        "Idle per-device config locks should be garbage-collected");
  }
}
