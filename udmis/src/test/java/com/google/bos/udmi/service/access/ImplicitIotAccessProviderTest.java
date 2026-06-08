package com.google.bos.udmi.service.access;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.support.QueueFullException;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.ConnectionBroker;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
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
  }
}
