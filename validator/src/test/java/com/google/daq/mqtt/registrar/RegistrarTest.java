package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.TestCommon.ALT_REGISTRY;
import static com.google.daq.mqtt.TestCommon.DEVICE_ID;
import static com.google.daq.mqtt.TestCommon.MOCK_SITE;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.daq.mqtt.TestCommon.SITE_REGION;
import static com.google.daq.mqtt.registrar.LocalDevice.EXCEPTION_VALIDATING;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CREATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.DELETE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.UPDATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.MOCK_DEVICE_ID;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.IotMockProvider;
import com.google.daq.mqtt.util.IotMockProvider.ActionType;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;

/**
 * Test suite for basic registrar functionality.
 */
public class RegistrarTest {

  public static final String REGISTRY_SUFFIX = "%X";
  private static final Set<String> ALLOWED_DEVICE_IDS = ImmutableSet.of("bacnet_29314",
      "bacnet-29138", "AHU-0001");
  private static final Set<String> ILLEGAL_DEVICE_IDS = ImmutableSet.of("bacnet/293124",
      "BACnet.213214");
  private static final Set<String> ALLOWED_POINT_NAMES = ImmutableSet.of("kWh", "Current",
      "analogValue_13", "analogValue-13", "AV_-13", "Electric_L_OrchesTra");
  private static final Set<String> ILLEGAL_POINT_NAMES = ImmutableSet.of("av/13", "av%13", "23,11",
      "IB#12", "qoduwqdwq.jdiwqd");

  @SuppressWarnings("unchecked")
  private static double getValidatingSize(Map<String, Object> summary) {
    return ((Map<String, Object>) summary.get(EXCEPTION_VALIDATING)).size();
  }

  private void assertErrorSummaryValidateSuccess(Map<String, Object> summary) {
    if ((summary == null) || (summary.get(EXCEPTION_VALIDATING) == null)
        || (getValidatingSize(summary) == 0)) {
      return;
    }
    fail(summary.get(EXCEPTION_VALIDATING).toString());
  }

  private void assertErrorSummaryValidateFailure(Map<String, Object> summary) {
    if ((summary == null) || (summary.get(EXCEPTION_VALIDATING) == null)) {
      fail("Error summary for Validating key is null");
    }
    if (getValidatingSize(summary) == 0) {
      fail("Error summary for Validating key is size 0");
    }
  }

  private Registrar getRegistrar(List<String> args) {
    try {
      List<String> registrarArgs = new ArrayList<>();
      registrarArgs.add(MOCK_SITE);
      registrarArgs.add(MOCK_PROJECT);
      ifNotNullThen(args, () -> registrarArgs.addAll(args));
      return new Registrar().processArgs(registrarArgs);
    } catch (Exception e) {
      throw new RuntimeException("While getting test registrar", e);
    }
  }

  @Test
  public void metadataValidateFailureTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute();
    assertErrorSummaryValidateFailure(registrar.getLastErrorSummary());
  }

  @Test
  public void blockDevicesTest() {
    List<MockAction> mockActions = getMockedActions(ImmutableList.of("-b"));
    List<MockAction> blockActions = filterActions(mockActions, BLOCK_DEVICE_ACTION);
    assertEquals("block action count", 1, blockActions.size());
    assertEquals("block action distinct devices", blockActions.size(),
        blockActions.stream().map(action -> action.deviceId).collect(
            Collectors.toSet()).size());
    blockActions.forEach(action -> assertEquals("device blocked " + action.deviceId,
        action.deviceId.equals(MOCK_DEVICE_ID), action.data));
  }

  @Test
  public void registryVariantsTests() {
    checkRegistration(false, false, REGISTRY_ID);
    checkRegistration(true, false, ALT_REGISTRY);
    checkRegistration(false, true, REGISTRY_ID + REGISTRY_SUFFIX);
    checkRegistration(true, true, ALT_REGISTRY + REGISTRY_SUFFIX);
  }

  private void checkRegistration(boolean useAlternate, boolean useSuffix, String expectedRegistry) {
    List<String> args = new ArrayList<>();
    ifTrueThen(useAlternate, () -> args.addAll(ImmutableList.of("-a", ALT_REGISTRY)));
    ifTrueThen(useSuffix, () -> args.addAll(ImmutableList.of("-e", REGISTRY_SUFFIX)));
    Registrar registrar = getRegistrar(args);
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
  public void checkAllowedDeviceIds() {
    Set<String> okAddedIds = new HashSet<>();
    Set<String> trialDeviceIds = Sets.union(ILLEGAL_DEVICE_IDS, ALLOWED_DEVICE_IDS);
    trialDeviceIds.forEach(deviceId -> {
      try {
        Registrar registrar = getRegistrar(ImmutableList.of());
        registrar.execute(() -> {
          Map<String, LocalDevice> localDevices = registrar.getLocalDevices();
          localDevices.put(deviceId, localDevices.get(DEVICE_ID).duplicate(deviceId));
        });
        okAddedIds.add(deviceId); // Record devices that don't throw an exception.
      } catch (Exception e) {
        System.err.println("Failed: " + deviceId + " because " + friendlyStackTrace(e));
      }
    });
    assertEquals("set of allowed device ids", ALLOWED_DEVICE_IDS, okAddedIds);
  }

  @Test
  public void checkAllowedPointNames() {
    Set<String> okAddedNames = new HashSet<>();
    Set<String> trialPointNames = Sets.union(ILLEGAL_POINT_NAMES, ALLOWED_POINT_NAMES);
    trialPointNames.forEach(pointName -> {
      try {
        Registrar registrar = getRegistrar(ImmutableList.of());
        registrar.execute(() -> {
          Map<String, LocalDevice> localDevices = registrar.getLocalDevices();
          Metadata metadata = localDevices.get(DEVICE_ID).getMetadata();
          metadata.pointset.points.put(pointName, new PointPointsetModel());
        });
        okAddedNames.add(pointName); // Record names that don't throw an exception.
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("Failed: " + pointName + " because " + friendlyStackTrace(e));
      }
    });
    assertEquals("set of allowed point names", ALLOWED_POINT_NAMES, okAddedNames);
  }

  @Test
  public void basicUpdates() {
    List<MockAction> mockActions = getMockedActions(ImmutableList.of());

    List<MockAction> blockActions = filterActions(mockActions, BLOCK_DEVICE_ACTION);
    assertEquals("block action count", 0, blockActions.size());

    List<MockAction> createActions = filterActions(mockActions, CREATE_DEVICE_ACTION);
    assertEquals("Devices created", 2, createActions.size());

    List<MockAction> deleteActions = filterActions(mockActions, DELETE_DEVICE_ACTION);
    assertEquals("Devices deleted", 2, deleteActions.size());

    List<MockAction> updateActions = filterActions(mockActions, UPDATE_DEVICE_ACTION);
    assertEquals("Devices updated", 3, updateActions.size());

    assertTrue("all devices not blocked", updateActions.stream().allMatch(this::isNotBlocking));
    List<MockAction> bindActions = filterActions(mockActions, BIND_DEVICE_ACTION);
    assertEquals("bind actions", 1, bindActions.size());
    assertTrue("bind gateway",
        bindActions.stream().allMatch(action -> action.data.equals("GAT-123")));
    assertEquals("bind devices", ImmutableSet.of("AHU-22"),
        bindActions.stream().map(action -> action.deviceId).collect(
            Collectors.toSet()));
  }

  private Boolean isNotBlocking(MockAction action) {
    return !TRUE.equals(((CloudModel) action.data).blocked);
  }

  private List<MockAction> filterActions(List<MockAction> mockActions, ActionType actionKey) {
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
