package com.google.bos.udmi.service.access;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.ConnectionBroker;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import com.google.bos.udmi.service.support.MosquittoBroker;
import com.google.bos.udmi.service.support.QueueFullException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.Credential;
import udmi.schema.Credential.Key_format;
import udmi.schema.IotAccess;

class ImplicitIotAccessProviderTest {

  private static final String TEST_REGISTRY = "test-reg";
  private static final String TEST_DEVICE = "test-dev";
  private static final String TEST_PASSWORD = "supersecret";
  private static final String CLIENT_ID = "/r/test-reg/d/test-dev";

  private Map<String, String> store;
  private ImplicitIotAccessProvider provider;
  private ConnectionBroker mockBroker;

  @BeforeEach
  void setUp() throws Exception {
    UdmiServicePod.resetForTest();
    store = new HashMap<>();
    IotDataProvider mockDatabase = mock(IotDataProvider.class);
    when(mockDatabase.ref()).thenAnswer(inv -> new FakeDataRef(store));
    UdmiServicePod.putComponent("database", () -> mockDatabase);

    ReflectProcessor mockReflect = mock(ReflectProcessor.class);
    UdmiServicePod.putComponent("reflect", () -> mockReflect);

    IotAccess iotAccess = new IotAccess();
    iotAccess.options = "enable, use_password=" + TEST_PASSWORD + ", disable_logging=true";
    provider = new ImplicitIotAccessProvider(iotAccess);
    provider.activate();

    mockBroker = mock(ConnectionBroker.class);
    when(mockBroker.authorize(anyString(), any())).thenReturn(completedFuture(null));
    when(mockBroker.bindGateway(anyString(), anyString())).thenReturn(completedFuture(null));
    when(mockBroker.unbindGateway(anyString(), anyString())).thenReturn(completedFuture(null));

    Field brokerField = ImplicitIotAccessProvider.class.getDeclaredField("broker");
    brokerField.setAccessible(true);
    brokerField.set(provider, mockBroker);
  }

  @AfterEach
  void tearDown() {
    if (provider != null) {
      provider.shutdown();
    }
    UdmiServicePod.resetForTest();
  }

  @Test
  void testAuthorizeWithCredentials() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.CREATE;
    Credential credential = new Credential();
    credential.key_format = Key_format.RS_256;
    credential.key_data = "fake_key_data";
    cloudModel.credentials = List.of(credential);

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verify(mockBroker).authorize(eq(CLIENT_ID), eq(TEST_PASSWORD));
  }

  @Test
  void testAuthorizeWithAuthType() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.CREATE;
    cloudModel.auth_type = Auth_type.RS_256;

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verify(mockBroker).authorize(eq(CLIENT_ID), eq(TEST_PASSWORD));
  }

  @Test
  void testAuthorizeWithQueueFullRetry() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.CREATE;
    cloudModel.auth_type = Auth_type.RS_256;

    // Mock broker to throw QueueFullException then succeed
    when(mockBroker.authorize(eq(CLIENT_ID), eq(TEST_PASSWORD)))
        .thenThrow(new QueueFullException("Queue full"))
        .thenReturn(completedFuture(null));

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verify(mockBroker, times(2)).authorize(eq(CLIENT_ID), eq(TEST_PASSWORD));
  }

  @Test
  void testDoNotAuthorizeWithoutCredentialsOrAuthType() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.CREATE;

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verifyNoInteractions(mockBroker);
  }

  @Test
  void testBindDevicesToGatewayNew() {
    store.put("r/test-reg/d/test-dev:num_id", "12345");
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.BIND;
    cloudModel.functions_ver = 1;
    cloudModel.gateway = new udmi.schema.GatewayModel();
    cloudModel.gateway.proxy_ids = List.of("proxy-device-2");

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verify(mockBroker).bindGateway(
        eq("/r/test-reg/d/test-dev"),
        eq("/r/test-reg/d/proxy-device-2"));
    assertEquals("PROXIED", store.get("r/test-reg/d/proxy-device-2:resource_type"));
  }

  @Test
  void testModelProxiedDevice() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.CREATE;
    cloudModel.resource_type = udmi.schema.CloudModel.Resource_type.PROXIED;

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    assertEquals("PROXIED", store.get("r/test-reg/d/test-dev:resource_type"));
  }

  @Test
  void testUnbindDevicesFromGatewayNew() {
    store.put("r/test-reg/d/test-dev:num_id", "12345");
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.UNBIND;
    cloudModel.functions_ver = 1;
    cloudModel.gateway = new udmi.schema.GatewayModel();
    cloudModel.gateway.proxy_ids = List.of("proxy-device-4");

    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);

    verify(mockBroker).unbindGateway(
        eq("/r/test-reg/d/test-dev"),
        eq("/r/test-reg/d/proxy-device-4"));
  }

  @Test
  void testBrokerAuthFalseNoInteractions() throws Exception {
    if (provider != null) {
      provider.shutdown();
    }
    IotAccess iotAccess = new IotAccess();
    iotAccess.options =
        "enable, use_password=" + TEST_PASSWORD + ", disable_logging=true, broker_auth=false";
    provider = new ImplicitIotAccessProvider(iotAccess);
    provider.activate();

    ConnectionBroker authDisabledBroker = mock(ConnectionBroker.class);
    Field brokerField = ImplicitIotAccessProvider.class.getDeclaredField("broker");
    brokerField.setAccessible(true);
    brokerField.set(provider, authDisabledBroker);

    CloudModel createModel = new CloudModel();
    createModel.operation = ModelOperation.CREATE;
    Credential credential = new Credential();
    credential.key_format = Key_format.RS_256;
    credential.key_data = "fake_key_data";
    createModel.credentials = List.of(credential);
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, createModel, null);

    store.put("r/test-reg/d/test-dev:num_id", "12345");
    CloudModel bindModel = new CloudModel();
    bindModel.operation = ModelOperation.BIND;
    bindModel.functions_ver = 1;
    bindModel.gateway = new udmi.schema.GatewayModel();
    bindModel.gateway.proxy_ids = List.of("proxy-device-2");
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, bindModel, null);

    CloudModel unbindModel = new CloudModel();
    unbindModel.operation = ModelOperation.UNBIND;
    unbindModel.functions_ver = 1;
    unbindModel.gateway = new udmi.schema.GatewayModel();
    unbindModel.gateway.proxy_ids = List.of("proxy-device-2");
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, unbindModel, null);

    CloudModel blockModel = new CloudModel();
    blockModel.operation = ModelOperation.BLOCK;
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, blockModel, null);

    CloudModel deleteModel = new CloudModel();
    deleteModel.operation = ModelOperation.DELETE;
    provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, deleteModel, null);

    store.put("r/test-reg/d/gateway-1:resource_type", "GATEWAY");
    store.put("r/test-reg/d/gateway-1:num_id", "9999");
    store.put("r/test-reg/d/gateway-1/c/bound_devices:bound-dev-1", "bound");
    store.put("r/test-reg/d/bound-dev-1:bound_to", "gateway-1");
    store.put("r/test-reg/d/bound-dev-1:bind_status", "bound");
    store.put("r/test-reg/d/bound-dev-1:num_id", "8888");

    CloudModel deleteGatewayModel = new CloudModel();
    deleteGatewayModel.operation = ModelOperation.DELETE;
    provider.modelDevice(TEST_REGISTRY, "gateway-1", deleteGatewayModel, null);

    verifyNoInteractions(authDisabledBroker);
  }

  @Test
  void testDeleteDeviceWithoutNumIdInStore() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = ModelOperation.DELETE;

    CloudModel reply = provider.modelDevice(TEST_REGISTRY, TEST_DEVICE, cloudModel, null);
    assertEquals(ModelOperation.DELETE, reply.operation);
    String expectedNumId = ImplicitIotAccessProvider.hashedDeviceId(TEST_REGISTRY, TEST_DEVICE);
    assertEquals(expectedNumId, reply.num_id);
  }

  @Test
  void testMosquittoDynsecMinIntervalMsOption() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.options =
        "enable, use_password=" + TEST_PASSWORD
            + ", disable_logging=true, mosquitto_dynsec_min_interval_ms=500";
    ImplicitIotAccessProvider customProvider = new ImplicitIotAccessProvider(iotAccess);
    try {
      MosquittoBroker customBroker = (MosquittoBroker) customProvider.getBroker();
      org.junit.jupiter.api.Assertions.assertEquals(
          500L, customBroker.getMinPublishIntervalMs());
    } finally {
      customProvider.shutdown();
    }
  }

  @Test
  void testMosquittoDynsecJitterRatioOption() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.options =
        "enable, use_password=" + TEST_PASSWORD
            + ", disable_logging=true, mosquitto_dynsec_min_interval_ms=500"
            + ", mosquitto_dynsec_jitter_ratio=0.5";
    ImplicitIotAccessProvider customProvider = new ImplicitIotAccessProvider(iotAccess);
    try {
      MosquittoBroker customBroker = (MosquittoBroker) customProvider.getBroker();
      org.junit.jupiter.api.Assertions.assertEquals(
          500L, customBroker.getMinPublishIntervalMs());
      org.junit.jupiter.api.Assertions.assertEquals(
          0.5, customBroker.getJitterRatio());
    } finally {
      customProvider.shutdown();
    }
  }

  class FakeDataRef extends DataRef {
    private final Map<String, String> data;

    public FakeDataRef(Map<String, String> data) {
      this.data = data;
    }

    private String getKeyPath(String key) {
      return (registryId != null ? "r/" + registryId : "")
          + (deviceId != null ? "/d/" + deviceId : "")
          + (collection != null ? "/c/" + collection : "")
          + ":"
          + key;
    }

    @Override
    public void delete(String key) {
      data.remove(getKeyPath(key));
    }

    @Override
    public Map<String, String> entries() {
      String prefix = getKeyPath("");
      Map<String, String> res = new HashMap<>();
      for (Map.Entry<String, String> entry : data.entrySet()) {
        if (entry.getKey().startsWith(prefix)) {
          res.put(entry.getKey().substring(prefix.length()), entry.getValue());
        }
      }
      return res;
    }

    @Override
    public String get(String key) {
      return data.get(getKeyPath(key));
    }

    @Override
    public AutoCloseable lock() {
      return () -> {};
    }

    @Override
    public void put(String key, String value) {
      data.put(getKeyPath(key), value);
    }

    @Override
    public void update(Map<String, String> puts, Set<String> deletes) {
      if (puts != null) {
        puts.forEach(this::put);
      }
      if (deletes != null) {
        deletes.forEach(this::delete);
      }
    }

    @Override
    public boolean updateIfMatch(String matchKey, String expectedValue, Map<String, String> puts,
        Set<String> deletes) {
      String current = get(matchKey);
      if ((expectedValue == null && current == null)
          || (expectedValue != null && expectedValue.equals(current))) {
        update(puts, deletes);
        return true;
      }
      return false;
    }
  }
}
