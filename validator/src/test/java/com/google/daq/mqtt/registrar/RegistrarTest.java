package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.util.IotMockProvider.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.MOCK_DEVICE_ID;
import static com.google.daq.mqtt.util.IotMockProvider.UPDATE_DEVICE_ACTION;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.services.cloudiot.v1.model.Device;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Test suite for basic registrar functionality.
 */
public class RegistrarTest {

  private static final String PROJECT_ID = "unit-testing";
  private static final String SITE_PATH = "../sites/udmi_site_model";
  private static final String TOOL_ROOT = "../";

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

  private Registrar getRegistrar(List<String> args) {
    try {
      Registrar registrar = new Registrar();
      registrar.setSitePath(SITE_PATH);
      registrar.setProjectId(PROJECT_ID);
      registrar.setToolRoot(TOOL_ROOT);
      if (args != null) {
        Registrar.processArgs(new ArrayList<>(args), registrar);
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
    List<MockAction> mockActions = getMockedActions(ImmutableList.of("-u", "--", "AHU-1"));
    mockActions.forEach(action -> assertEquals("Mocked device " + action.action, "AHU-1",
        action.deviceId));
    assertTrue("Device is not blocked", filterActions(mockActions, BLOCK_DEVICE_ACTION).stream()
        .allMatch(action -> action.data.equals(TRUE)));
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
