package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.TestCommon.ALT_REGISTRY;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.daq.mqtt.TestCommon.SITE_DIR;
import static com.google.daq.mqtt.TestCommon.SITE_REGION;
import static com.google.daq.mqtt.TestCommon.TOOL_ROOT;
import static com.google.daq.mqtt.util.IotMockProvider.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.MOCK_DEVICE_ID;
import static com.google.daq.mqtt.util.IotMockProvider.UPDATE_DEVICE_ACTION;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.services.cloudiot.v1.model.Device;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.IotMockProvider;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Test suite for basic registrar functionality.
 */
public class RegistrarTest {

  public static final String REGISTRY_SUFFIX = "%X";

  private void assertErrorSummaryValidateSuccess(Map<String, Map<String, String>> summary) {
    if ((summary == null) || (summary.get("Validating") == null)
        || (summary.get("Validating").size() == 0)) {
      return;
    }
    fail(summary.get("Validating").toString());
  }

  private void assertErrorSummaryValidateFailure(Map<String, Map<String, String>> summary) {
    if ((summary == null) || (summary.get("Validating") == null)) {
      fail("Error summary for Validating key is null");
    }
    if (summary.get("Validating").size() == 0) {
      fail("Error summary for Validating key is size 0");
    }
  }

  private Registrar getRegistrar(Consumer<ExecutionConfiguration> profileUpdater,
      List<String> args) {
    ExecutionConfiguration configuration = new ExecutionConfiguration();
    configuration.alt_registry = ALT_REGISTRY;
    configuration.site_model = SITE_DIR;
    configuration.project_id = MOCK_PROJECT;
    profileUpdater.accept(configuration);
    Registrar registrar = new Registrar();
    registrar.setToolRoot(TOOL_ROOT);
    return registrar.processProfile(configuration).processArgs(args);
  }

  private Registrar getRegistrar(List<String> args) {
    try {
      Registrar registrar = new Registrar();
      registrar.setSitePath(SITE_DIR);
      registrar.setProjectId(MOCK_PROJECT, null);
      registrar.setToolRoot(TOOL_ROOT);
      if (args != null) {
        registrar.processArgs(new ArrayList<>(args));
      }
      return registrar;
    } catch (Exception e) {
      throw new RuntimeException("While getting test registrar", e);
    }
  }

  @Test
  public void metadataValidateSuccessTest() {
    Registrar registrar = getRegistrar(null);
    registrar.execute();
    assertErrorSummaryValidateSuccess(registrar.getLastErrorSummary());
  }

  @Test
  public void metadataValidateFailureTest() {
    Registrar registrar = getRegistrar(ImmutableList.of("-t"));
    registrar.execute();
    assertErrorSummaryValidateFailure(registrar.getLastErrorSummary());
  }

  @Test
  public void noBlockDevicesTest() {
    List<MockAction> mockActions = getMockedActions(ImmutableList.of("--", "AHU-1"));
    mockActions.forEach(action -> assertEquals("Mocked device " + action.action, "AHU-1",
        action.deviceId));
    assertTrue("Device is not blocked", filterActions(mockActions, BLOCK_DEVICE_ACTION).stream()
        .allMatch(action -> action.data.equals(TRUE)));
  }

  @Test
  public void registryVariantsTests() {
    checkRegistration(false, false, REGISTRY_ID);
    checkRegistration(false, true, REGISTRY_ID + REGISTRY_SUFFIX);
    checkRegistration(true, false, ALT_REGISTRY);
    checkRegistration(true, true, ALT_REGISTRY + REGISTRY_SUFFIX);
  }

  private void checkRegistration(boolean useAlternate, boolean useSuffix, String expectedRegistry) {
    List<String> args = useAlternate ? ImmutableList.of("-a") : ImmutableList.of();
    Registrar registrar = getRegistrar(profile -> {
      profile.registry_suffix = useSuffix ? REGISTRY_SUFFIX : null;
    }, args);
    registrar.execute();
    List<Object> mockActions = registrar.getMockActions();
    String mockClientString = IotMockProvider.mockClientString(MOCK_PROJECT,
        expectedRegistry, SITE_REGION);
    List<Object> mismatchItems = mockActions.stream().map(action -> ((MockAction) action).client)
        .filter(client -> !client.equals(mockClientString))
        .collect(Collectors.toList());
    assertEquals("clients not matching " + mockClientString, ImmutableList.of(), mismatchItems);
  }

  @Test
  public void basicUpdates() {
    List<MockAction> mockActions = getMockedActions(ImmutableList.of("-u"));
    List<MockAction> blockActions = filterActions(mockActions, BLOCK_DEVICE_ACTION);
    assertEquals("block action count", 1, blockActions.size());
    assertEquals("block action distinct devices", blockActions.size(),
        blockActions.stream().map(action -> action.deviceId).collect(
            Collectors.toSet()).size());
    blockActions.forEach(action -> assertEquals("device blocked " + action.deviceId,
        action.deviceId.equals(MOCK_DEVICE_ID), action.data));
    List<MockAction> updateActions = filterActions(mockActions, UPDATE_DEVICE_ACTION);
    assertEquals("Devices updated", 4, updateActions.size());
    assertTrue("all devices not blocked", updateActions.stream().allMatch(this::isNotBlocking));
    List<MockAction> bindActions = filterActions(mockActions, BIND_DEVICE_ACTION);
    assertEquals("bind actions", 2, bindActions.size());
    assertTrue("bind gateway",
        bindActions.stream().allMatch(action -> action.data.equals("GAT-123")));
    assertEquals("bind devices", ImmutableSet.of("SNS-4", "AHU-22"),
        bindActions.stream().map(action -> action.deviceId).collect(
            Collectors.toSet()));
  }

  private Boolean isNotBlocking(MockAction action) {
    return !TRUE.equals(((Device) action.data).getBlocked());
  }

  private List<MockAction> filterActions(List<MockAction> mockActions, String actionKey) {
    return mockActions.stream()
        .filter(action -> action.action.equals(actionKey))
        .collect(Collectors.toList());
  }

  private List<MockAction> getMockedActions(ImmutableList<String> optArgs) {
    Registrar registrar = getRegistrar(optArgs);
    registrar.execute();
    return registrar.getMockActions().stream().map(a -> (MockAction) a)
        .collect(Collectors.toList());
  }
}
