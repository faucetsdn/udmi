package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MIN;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Tests for the reflect processor function.
 */
public class ReflectProcessorTest extends ProcessorTestBase {

  public final String transactionId = Long.toString(System.currentTimeMillis());

  private void activeTestInstance(Runnable action) {
    verify(provider, times(1)).activate();
    verify(provider, times(0)).shutdown();
    action.run();
    terminateAndWait();
    verify(provider, times(1)).activate();
    verify(provider, times(1)).shutdown();
  }

  @NotNull
  private Envelope makeEnvelope(SubType subType, SubFolder subFolder) {
    Envelope envelope = new Envelope();
    envelope.subType = subType;
    envelope.subFolder = subFolder;
    envelope.projectId = TEST_NAMESPACE;
    envelope.deviceId = TEST_DEVICE;
    envelope.deviceRegistryId = TEST_REGISTRY;
    return envelope;
  }

  private Bundle makeModelBundle(CloudModel model) {
    Envelope reflect = makeTestEnvelope();
    reflect.transactionId = transactionId;
    reflect.subType = SubType.MODEL;
    reflect.payload = encodeBase64(stringify(model));
    return new Bundle(makeEnvelope(SubType.MODEL, SubFolder.UDMI), reflect);
  }

  private Bundle makeUdmiStateBundle(boolean asError) {
    return new Bundle(makeEnvelope(null, null), makeUdmiStateMessage(asError));
  }

  @NotNull
  private Object makeUdmiStateMessage(boolean asError) {
    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = TEST_USER;
    udmiState.timestamp = TEST_TIMESTAMP;
    return asError ? udmiState : ImmutableMap.of(SubFolder.UDMI.value(), udmiState);
  }

  private void validateUdmiConfig(UdmiConfig config) {
    assertEquals(FUNCTIONS_VERSION_MIN, config.setup.functions_min, "min func version");
    assertEquals(FUNCTIONS_VERSION_MAX, config.setup.functions_max, "min func version");
    assertEquals(TEST_USER, config.setup.deployed_by, "deployed by user");
    assertEquals(TEST_TIMESTAMP, config.setup.deployed_at, "deployed at time");
  }

  /**
   * Test that the basic udmi-reflect state/config handshake works. When a client connects it
   * updates the state of the device entry, which then should trigger the underlying logic to output
   * an updated config message.
   */
  @Test
  public void initialInitExchange() {
    activeTestInstance(() -> getReverseDispatcher().publish(makeUdmiStateBundle(false)));

    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Function> configCaptor = ArgumentCaptor.forClass(Function.class);

    //noinspection unchecked
    verify(provider, times(1)).modifyConfig(eq(TEST_REGISTRY), eq(TEST_DEVICE),
        (Function<Entry<Long, String>, String>) configCaptor.capture());

    @SuppressWarnings("unchecked")
    String newConfig = (String) configCaptor.getValue().apply(null);

    Map<String, Object> stringObjectMap = toMap(newConfig);
    UdmiConfig udmi =
        convertToStrict(UdmiConfig.class, stringObjectMap.get(SubFolder.UDMI.value()));
    validateUdmiConfig(udmi);
  }

  @BeforeEach
  public void initializeInstance() {
    initializeTestInstance(ReflectProcessor.class);
  }

  /**
   * Test reflector initial state/config exchange sequence with an invalid bundle.
   */
  @Test
  public void invalidInitExchange() {
    // This bundle is invalid because the envelope has no payload, so expect an error.
    activeTestInstance(() -> getReverseDispatcher().publish(makeUdmiStateBundle(true)));

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(anyString(), anyString(), eq(SubFolder.UDMI),
        commandCaptor.capture());
    List<String> allValues = commandCaptor.getAllValues();
    assertEquals(1, allValues.size(), "Expected one sent commands");
    Envelope errorEnvelope = JsonUtil.fromStringStrict(Envelope.class, allValues.get(0));
    assertEquals(SubFolder.ERROR, errorEnvelope.subFolder, "expected error response");
  }

  @Test
  public void modelDevice() {
    CloudModel returnModel = new CloudModel();
    returnModel.operation = Operation.CREATE;
    when(provider.modelDevice(anyString(), anyString(), notNull())).thenReturn(returnModel);

    CloudModel requestModel = new CloudModel();
    requestModel.operation = Operation.BIND;
    activeTestInstance(() -> getReverseDispatcher().publish(makeModelBundle(requestModel)));
    verify(provider, times(1)).modelDevice(eq(TEST_REGISTRY), eq(TEST_DEVICE), eq(requestModel));

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(provider, times(1)).sendCommand(eq(TEST_REGISTRY), eq(TEST_DEVICE), eq(SubFolder.UDMI),
        commandCaptor.capture());
    Envelope envelope = JsonUtil.fromStringStrict(Envelope.class, commandCaptor.getValue());
    assertEquals(transactionId, envelope.transactionId);
  }
}