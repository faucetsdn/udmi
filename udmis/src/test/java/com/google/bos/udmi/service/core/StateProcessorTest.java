package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.CleanDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Config;
import udmi.schema.Envelope;
import udmi.schema.GatewayState;
import udmi.schema.Operation;
import udmi.schema.State;
import udmi.schema.StateSystemOperation;
import udmi.schema.SystemConfig;
import udmi.schema.SystemState;
import udmi.schema.TestingSystemConfig;

/**
 * Tests for the StateHandler class, used by UDMIS to process device state updates.
 */
public class StateProcessorTest extends ProcessorTestBase {

  public static final Date INITIAL_LAST_START = CleanDateFormat.cleanDate(new Date(12981837));
  public static final long CONFIG_VERSION = 23L;
  private static final String LEGACY_STATE_MESSAGE_FILE = "src/test/messages/legacy_state.json";

  private boolean contains(Predicate<Object> objectPredicate) {
    return captured.stream().anyMatch(objectPredicate);
  }

  private Config getTestConfig() {
    Config config = new Config();
    config.system = new SystemConfig();
    config.system.operation = new Operation();
    config.system.testing = new TestingSystemConfig();
    return config;
  }

  private Bundle getTestStateBundle(boolean includeGateway, boolean lastStart) {
    return new Bundle(getTestStateEnvelope(), getTestStateMessage(includeGateway, lastStart));
  }

  @NotNull
  private Envelope getTestStateEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.deviceId = TEST_DEVICE;
    return envelope;
  }

  @NotNull
  private State getTestStateMessage(boolean includeGateway, boolean includeLastStart) {
    State stateMessage = new State();
    stateMessage.version = TEST_VERSION;
    stateMessage.gateway = includeGateway ? new GatewayState() : null;
    stateMessage.system = new SystemState();
    if (includeLastStart) {
      stateMessage.system.operation = new StateSystemOperation();
      stateMessage.system.operation.last_start = INITIAL_LAST_START;
    }
    return stateMessage;
  }

  private void initializeTestInstance() {
    initializeTestInstance(StateProcessor.class);
  }

  private Config processLastStart(Config testConfig) {
    initializeTestInstance();
    getReverseDispatcher().publish(getTestStateBundle(false, true));

    terminateAndWait();

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Function> configCaptor = ArgumentCaptor.forClass(Function.class);

    //noinspection unchecked
    verify(provider, times(1)).modifyConfig(
        eq(makeTestEnvelope(true)), (Function<Entry<Long, String>, String>) configCaptor.capture());

    //noinspection unchecked
    Function<Entry<Long, String>, String> configMunger = configCaptor.getValue();
    return ifNotNullGet(configMunger.apply(Map.entry(CONFIG_VERSION, stringify(testConfig))),
        newConfig -> fromStringStrict(Config.class, newConfig));
  }

  @Test
  public void lastStartEmpty() {
    Config testConfig = getTestConfig();
    Config newConfig = processLastStart(testConfig);
    assertNull(newConfig);
  }

  @Test
  public void lastStartIgnored() {
    Config testConfig = getTestConfig();
    testConfig.system.operation.last_start = CleanDateFormat.cleanDate(new Date(0));
    Config newConfig = processLastStart(testConfig);
    assertEquals(INITIAL_LAST_START, newConfig.system.operation.last_start, "new last_start");
    assertEquals((int) CONFIG_VERSION, newConfig.system.testing.config_base, "config_base");
  }

  @Test
  public void lastStartNull() {
    assertNull(processLastStart(null), "updated config from null");
  }

  @Test
  public void lastStartUpdate() {
    Config testConfig = getTestConfig();
    testConfig.system.operation.last_start = CleanDateFormat.cleanDate();
    Config newConfig = processLastStart(testConfig);
    assertNull(newConfig, "new last_start");
  }

  @Test
  public void legacyStateMessage() {
    StateProcessor processor = initializeTestInstance(StateProcessor.class);
    Object message = loadFileRequired(Object.class, LEGACY_STATE_MESSAGE_FILE);
    dispatcher.withEnvelopeFor(new Envelope(), message, () -> processor.defaultHandler(message));
    terminateAndWait();
    assertEquals(3, captured.size(), "expected captured messages");
  }

  /**
   * Test that a state update with multiple sub-blocks results in the expected two messages.
   */
  @Test
  public void multiExpansion() {
    initializeTestInstance();

    getReverseDispatcher().publish(getTestStateBundle(true, false));

    terminateAndWait();

    assertEquals(3, captured.size(), "unexpected received message count");
    assertTrue(contains(message -> message instanceof StateUpdate), "has StateUpdate");
    assertTrue(contains(message -> message instanceof SystemState), "has SystemState");
    assertTrue(contains(message -> message instanceof GatewayState), "has GatewayState");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");
  }

  /**
   * Test that a state update with one sub-block results in a received message of the proper type.
   */
  @Test
  public void singleExpansion() {
    initializeTestInstance();

    getReverseDispatcher().publish(getTestStateBundle(false, false));

    terminateAndWait();

    assertEquals(2, captured.size(), "unexpected received message count");
    assertTrue(contains(msg -> msg instanceof SystemState), "expected SystemState message");
    assertTrue(contains(msg -> msg instanceof StateUpdate), "expected StateUpdate message");
    assertEquals(0, getExceptionCount(), "exception count");
    assertEquals(1, getDefaultCount(), "default handler count");

    verify(provider, never()).modifyConfig(eq(makeTestEnvelope(false)), any());
  }

  /**
   * Test that receiving an invalid message results in the appropriate exception handler being
   * called.
   */
  @Test
  public void stateException() {
    initializeTestInstance();
    Bundle bundle = new Bundle();
    bundle.envelope.transactionId = MessageBase.ERROR_MESSAGE_MARKER;
    bundle.message = "hello";
    getReverseDispatcher().publish(bundle);

    terminateAndWait();

    assertEquals(0, captured.size(), "unexpected received message count");
    assertEquals(1, getExceptionCount(), "exception count");
    assertEquals(0, getDefaultCount(), "default handler count");
  }
}
