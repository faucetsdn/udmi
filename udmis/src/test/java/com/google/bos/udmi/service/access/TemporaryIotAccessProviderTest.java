package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.Envelope;
import udmi.schema.IotAccess;

/**
 * Test class for TemporaryIotAccessProvider.
 */
public class TemporaryIotAccessProviderTest {

  private TemporaryIotAccessProvider provider;
  private static final String REGISTRY_ID = "test-registry";
  private static final String DEVICE_ID = "test-device";

  /**
   * Set up the provider for testing.
   */
  @BeforeEach
  public void setUp() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.provider = IotAccess.IotProvider.TEMPORARY;
    provider = new TemporaryIotAccessProvider(iotAccess);
  }

  @Test
  public void testModelDeviceCreate() {
    CloudModel createModel = new CloudModel();
    createModel.operation = ModelOperation.CREATE;
    CloudModel result = provider.modelDevice(REGISTRY_ID, DEVICE_ID, createModel, null);
    assertEquals(createModel, result);

    CloudModel fetched = provider.fetchDevice(REGISTRY_ID, DEVICE_ID);
    assertNotNull(fetched);
    assertEquals(ModelOperation.CREATE, fetched.operation);
  }

  @Test
  public void testModelDeviceDelete() {
    CloudModel createModel = new CloudModel();
    createModel.operation = ModelOperation.CREATE;
    provider.modelDevice(REGISTRY_ID, DEVICE_ID, createModel, null);

    CloudModel deleteModel = new CloudModel();
    deleteModel.operation = ModelOperation.DELETE;
    provider.modelDevice(REGISTRY_ID, DEVICE_ID, deleteModel, null);

    CloudModel fetched = provider.fetchDevice(REGISTRY_ID, DEVICE_ID);
    assertNull(fetched);
  }

  @Test
  public void testListDevices() {
    CloudModel createModel1 = new CloudModel();
    createModel1.operation = ModelOperation.CREATE;
    provider.modelDevice(REGISTRY_ID, "device-1", createModel1, null);

    CloudModel createModel2 = new CloudModel();
    createModel2.operation = ModelOperation.CREATE;
    provider.modelDevice(REGISTRY_ID, "device-2", createModel2, null);

    List<String> progressList = new ArrayList<>();
    CloudModel listModel = provider.listDevices(REGISTRY_ID, progressList::add);

    assertNotNull(listModel.device_ids);
    assertEquals(2, listModel.device_ids.size());
    assertTrue(listModel.device_ids.containsKey("device-1"));
    assertTrue(listModel.device_ids.containsKey("device-2"));

    assertEquals(2, progressList.size());
    assertTrue(progressList.contains("device-1"));
    assertTrue(progressList.contains("device-2"));
  }

  @Test
  public void testFetchConfig() {
    Entry<Long, String> config = provider.fetchConfig(REGISTRY_ID, DEVICE_ID);
    assertNotNull(config);
    assertEquals(0L, config.getKey());
    assertEquals("{}", config.getValue());
  }

  @Test
  public void testUpdateConfig() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = REGISTRY_ID;
    envelope.deviceId = DEVICE_ID;
    String newConfig = "{\"test\":true}";
    String result = provider.updateConfig(envelope, newConfig, 1L);
    assertEquals(newConfig, result);
    Entry<Long, String> fetched = provider.fetchConfig(REGISTRY_ID, DEVICE_ID);
    assertEquals(1L, fetched.getKey());
    assertEquals(newConfig, fetched.getValue());
  }

  @Test
  public void testConcurrentAccess() throws InterruptedException {
    int threadCount = 20;
    int operationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(() -> {
        try {
          for (int j = 0; j < operationsPerThread; j++) {
            String devId = "device-" + threadId + "-" + j;
            CloudModel createModel = new CloudModel();
            createModel.operation = ModelOperation.CREATE;
            provider.modelDevice(REGISTRY_ID, devId, createModel, null);
            provider.listDevices(REGISTRY_ID, null);
            provider.fetchDevice(REGISTRY_ID, devId);
          }
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    CloudModel listModel = provider.listDevices(REGISTRY_ID, null);
    assertEquals(threadCount * operationsPerThread, listModel.device_ids.size());
  }

  @Test
  public void testIsEnabled() {
    assertTrue(provider.isEnabled());
  }
}
