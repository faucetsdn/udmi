package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.messaging.impl.MessageTestCore;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.ConnectionBroker;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.IotAccess;

/**
 * Unit tests for ImplicitIotAccessProvider.
 */
public class ImplicitIotAccessProviderTest extends MessageTestCore {

  private final IotDataProvider mockDatabase = mock(IotDataProvider.class);
  private final DataRef mockDataRef = new TestDataRef();
  private final ReflectProcessor mockReflect = mock(ReflectProcessor.class);
  private final ConnectionBroker mockBroker = mock(ConnectionBroker.class);

  /**
   * Setup for tests.
   */
  @BeforeEach
  public void setUp() {
    UdmiServicePod.resetForTest();
    UdmiServicePod.putComponent("database", () -> mockDatabase);
    UdmiServicePod.putComponent("reflect", () -> mockReflect);
    when(mockDatabase.ref()).thenReturn(mockDataRef);
    when(mockBroker.addEventListener(anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  private ImplicitIotAccessProvider getProvider() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.name = "test-provider";
    iotAccess.options = "enabled";
    ImplicitIotAccessProvider provider = new ImplicitIotAccessProviderMock(iotAccess);
    provider.activate();
    return provider;
  }

  private class ImplicitIotAccessProviderMock extends ImplicitIotAccessProvider {
    public ImplicitIotAccessProviderMock(IotAccess iotAccess) {
      super(iotAccess);
    }

    @Override
    ConnectionBroker getBroker() {
      return mockBroker;
    }
  }

  @Test
  public void testPasswordCredentialBehavior() {
    CloudModel model = new CloudModel();
    model.operation = ModelOperation.CREATE;
    Credential credential = new Credential();
    credential.key_format = Key_format.PASSWORD;
    credential.key_data = "test-password";
    model.credentials = ImmutableList.of(credential);

    ImplicitIotAccessProvider provider = getProvider();
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, model, null);

    // Requirement: password should NOT be stored in the database
    String authPass = mockDataRef.registry(TEST_REGISTRY).device(TEST_DEVICE).get("auth_pass");
    assertNull(authPass, "Password should not be stored");
  }

  @Test
  public void testRs256CredentialBehavior() {
    CloudModel model = new CloudModel();
    model.operation = ModelOperation.CREATE;
    Credential credential = new Credential();
    credential.key_format = Key_format.RS_256;
    credential.key_data = "test-key-data";
    model.credentials = ImmutableList.of(credential);

    ImplicitIotAccessProvider provider = getProvider();
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, model, null);

    // Requirement: RS_256 should be stored in auth_key
    // In our mock, property keys are prepended with ':' to simulate EtcdDataProvider behavior
    String authKey = mockDataRef.registry(TEST_REGISTRY).device(TEST_DEVICE).get("auth_key");
    assertEquals("test-key-data", authKey, "RS256 key should be stored as auth_key");

    // Explicitly check the internal mock data for the colon separator
    assertEquals("test-key-data", ((TestDataRef) mockDataRef).getRawData().get(":auth_key"));
  }

  @Test
  public void testMultipleCredentials() {
    CloudModel model = new CloudModel();
    model.operation = ModelOperation.CREATE;

    Credential passCred = new Credential();
    passCred.key_format = Key_format.PASSWORD;
    passCred.key_data = "test-password";

    Credential keyCred = new Credential();
    keyCred.key_format = Key_format.RS_256;
    keyCred.key_data = "test-key-data";

    model.credentials = ImmutableList.of(passCred, keyCred);

    ImplicitIotAccessProvider provider = getProvider();
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, model, null);

    // Requirement: auth_key should be stored, auth_pass should NOT
    assertEquals("test-key-data",
        mockDataRef.registry(TEST_REGISTRY).device(TEST_DEVICE).get("auth_key"));
    assertNull(mockDataRef.registry(TEST_REGISTRY).device(TEST_DEVICE).get("auth_pass"));
  }

  @Test
  public void testExistingPasswordIsRemoved() {
    // Simulate pre-existing password in database
    DataRef deviceRef = mockDataRef.registry(TEST_REGISTRY).device(TEST_DEVICE);
    deviceRef.put("auth_pass", "old-password");
    deviceRef.put("num_id", TEST_NUMID);
    assertEquals("old-password", deviceRef.get("auth_pass"));

    CloudModel model = new CloudModel();
    model.operation = ModelOperation.UPDATE;
    Credential credential = new Credential();
    credential.key_format = Key_format.RS_256;
    credential.key_data = "test-key-data";
    model.credentials = ImmutableList.of(credential);

    ImplicitIotAccessProvider provider = getProvider();
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, model, null);

    // Requirement: old password should be gone, new key should be there
    assertNull(deviceRef.get("auth_pass"), "Old password should be removed");
    assertEquals("test-key-data", deviceRef.get("auth_key"));
  }

  private static class TestDataRef extends DataRef {
    private final Map<String, String> data = new ConcurrentHashMap<>();

    @Override
    public void delete(String key) {
      data.remove(":" + key);
    }

    @Override
    public Map<String, String> entries() {
      Map<String, String> result = new HashMap<>();
      data.forEach((k, v) -> {
        if (k.startsWith(":")) {
          result.put(k.substring(1), v);
        } else {
          result.put(k, v);
        }
      });
      return result;
    }

    @Override
    public String get(String key) {
      return data.get(":" + key);
    }

    @Override
    public AutoCloseable lock() {
      return () -> {};
    }

    @Override
    public void put(String key, String value) {
      data.put(":" + key, value);
    }

    public Map<String, String> getRawData() {
      return data;
    }
  }
}
