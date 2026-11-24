package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.TestCommon.ALT_REGISTRY;
import static com.google.daq.mqtt.TestCommon.DEVICE_ID;
import static com.google.daq.mqtt.TestCommon.GATEWAY_ID;
import static com.google.daq.mqtt.TestCommon.MOCK_SITE;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.daq.mqtt.TestCommon.SITE_REGION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BIND_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.BLOCK_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.CREATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.DELETE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.ActionType.UPDATE_DEVICE_ACTION;
import static com.google.daq.mqtt.util.IotMockProvider.MOCK_DEVICE_ID;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.SiteModel.MOCK_CLEAN;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.IotMockProvider;
import com.google.daq.mqtt.util.IotMockProvider.ActionType;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import com.google.udmi.util.ExceptionMap.ExceptionCategory;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.LocalnetModel;
import udmi.schema.Metadata;
import udmi.schema.Parent;
import udmi.schema.PointPointsetModel;

/**
 * Test suite for basic registrar functionality.
 */
public class RegistrarTest {

  public static final String REGISTRY_SUFFIX = "%X";
  private static final Set<String> ALLOWED_DEVICE_IDS = ImmutableSet.of("bacnet_29314",
      "bacnet-29138", "AHU-0001");
  private static final Set<String> ILLEGAL_DEVICE_IDS = ImmutableSet.of("bacnet/293124",
      "BACnet@213214");
  private static final Set<String> ALLOWED_POINT_NAMES = ImmutableSet.of("kWh", "Current",
      "analogValue_13", "analogValue-13", "AV_-13", "Electric_L_OrchesTra");
  private static final Set<String> ILLEGAL_POINT_NAMES = ImmutableSet.of("av/13", "av%13", "23,11",
      "IB#12", "qoduwqdwq.jdiwqd");

  @SuppressWarnings("unchecked")
  private static double getValidatingSize(Map<String, Object> summary) {
    return ((Map<String, Object>) summary.get(ExceptionCategory.validation.toString())).size();
  }

  private void assertErrorSummaryValidateSuccess(Map<String, Object> summary) {
    if ((summary == null) || (summary.get(ExceptionCategory.validation.toString()) == null)
        || (getValidatingSize(summary) == 0)) {
      return;
    }
    fail(summary.get(ExceptionCategory.validation.toString()).toString());
  }

  private void assertErrorSummaryValidateFailure(Map<String, Object> summary) {
    if ((summary == null) || (summary.get(ExceptionCategory.validation.toString()) == null)) {
      fail("Error summary for Validating key is null");
    }
    if (getValidatingSize(summary) == 0) {
      fail("Error summary for Validating key is size 0");
    }
  }

  private Registrar getRegistrar(List<String> args) {
    return getRegistrar(false, args);
  }

  private Registrar getRegistrar(boolean cleanRegistry, List<String> args) {
    try {
      List<String> registrarArgs = new ArrayList<>();
      registrarArgs.add(MOCK_SITE);
      registrarArgs.add(cleanRegistry ? MOCK_CLEAN : MOCK_PROJECT);
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
    List<MockAction> mockActions = executeRegistrarPopulated(ImmutableList.of("-b"));
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
    File registrarConf = new File("out/registrar_conf.json");
    ExecutionConfiguration writtenConf =
        JsonUtil.loadFileRequired(ExecutionConfiguration.class, registrarConf);
    assertEquals("reconciled registrar_conf.json registry_suffix",
        useSuffix ? REGISTRY_SUFFIX : null, writtenConf.registry_suffix);
    registrar.execute();
    List<MockAction> mockActions = registrar.getMockActions();
    String mockClientString = IotMockProvider.mockClientString(MOCK_PROJECT,
        expectedRegistry, SITE_REGION);
    List<Object> mismatchItems = mockActions.stream().map(action -> action.client)
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
          Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
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
          Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
          Metadata metadata = localDevices.get(DEVICE_ID).getMetadata();
          PointPointsetModel pointModel = new PointPointsetModel();
          pointModel.units = "Celsius";
          metadata.pointset.points.put(pointName, pointModel);
        });
        boolean noneMatch = registrar.getWorkingDevices().entrySet().stream()
            .noneMatch(entry -> entry.getValue().hasCategory(ExceptionCategory.settings));
        if (noneMatch) {
          okAddedNames.add(pointName); // Record names that don't throw an exception.
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("Failed: " + pointName + " because " + friendlyStackTrace(e));
      }
    });
    assertEquals("set of allowed point names", ALLOWED_POINT_NAMES, okAddedNames);
  }

  @Test
  public void pointTypeValidationTest() {
    PointPointsetModel validNumeric = new PointPointsetModel();
    validNumeric.units = "Degrees-Celsius";
    validNumeric.baseline_value = 21.5;

    PointPointsetModel validMultistate = new PointPointsetModel();
    validMultistate.values = Map.of("0.0", "OFF", "1.0", "ON");
    validMultistate.baseline_value = "1.0";

    PointPointsetModel validString = new PointPointsetModel();
    validString.type = "string";
    validString.baseline_value = "NORMAL";

    PointPointsetModel validBinary = new PointPointsetModel();
    validBinary.type = "boolean";
    validBinary.baseline_value = true;

    assertTrue("valid numeric point", isPointModelValid(validNumeric));
    assertTrue("valid multistate point", isPointModelValid(validMultistate));
    assertTrue("valid string point", isPointModelValid(validString));
    assertTrue("valid binary point", isPointModelValid(validBinary));

    PointPointsetModel emptyPoint = new PointPointsetModel();
    assertTrue("empty point rejected", !isPointModelValid(emptyPoint));

    PointPointsetModel dualUnitsType = new PointPointsetModel();
    dualUnitsType.units = "No-units";
    dualUnitsType.type = "boolean";
    assertTrue("units + type rejected", !isPointModelValid(dualUnitsType));

    PointPointsetModel dualValuesType = new PointPointsetModel();
    dualValuesType.values = Map.of("0.0", "OFF", "1.0", "ON");
    dualValuesType.type = "string";
    assertTrue("values + type rejected", !isPointModelValid(dualValuesType));

    PointPointsetModel binaryWithRange = new PointPointsetModel();
    binaryWithRange.type = "boolean";
    binaryWithRange.range_min = 0.0;
    assertTrue("binary with range_min rejected", !isPointModelValid(binaryWithRange));

    PointPointsetModel badMultistateBaseline = new PointPointsetModel();
    badMultistateBaseline.values = Map.of("0.0", "OFF", "1.0", "ON");
    badMultistateBaseline.baseline_value = "ON";
    assertTrue("multistate with value instead of key baseline rejected",
        !isPointModelValid(badMultistateBaseline));

    PointPointsetModel singleValueMultistate = new PointPointsetModel();
    singleValueMultistate.values = Map.of("1.0", "ON");
    assertTrue("multistate with < 2 entries rejected", !isPointModelValid(singleValueMultistate));

    PointPointsetModel badBinaryBaseline = new PointPointsetModel();
    badBinaryBaseline.type = "boolean";
    badBinaryBaseline.baseline_value = "true";
    assertTrue("binary with string baseline rejected", !isPointModelValid(badBinaryBaseline));

    PointPointsetModel invalidType = new PointPointsetModel();
    invalidType.type = "integer";
    assertTrue("invalid point type rejected", !isPointModelValid(invalidType));

    PointPointsetModel validMissing = new PointPointsetModel();
    validMissing.type = "missing";
    assertTrue("valid missing point", isPointModelValid(validMissing));

    PointPointsetModel missingWithRef = new PointPointsetModel();
    missingWithRef.type = "missing";
    missingWithRef.ref = "AV1.present_value";
    assertTrue("missing point with ref rejected", !isPointModelValid(missingWithRef));

    PointPointsetModel missingWithExpr = new PointPointsetModel();
    missingWithExpr.type = "missing";
    missingWithExpr.expr = "AHU-1:supply_air_flowrate_sensor";
    assertTrue("missing point with expr rejected", !isPointModelValid(missingWithExpr));

    PointPointsetModel missingWithWritable = new PointPointsetModel();
    missingWithWritable.type = "missing";
    missingWithWritable.writable = true;
    assertTrue("missing point with writable rejected", !isPointModelValid(missingWithWritable));

    PointPointsetModel missingWithBaseline = new PointPointsetModel();
    missingWithBaseline.type = "missing";
    missingWithBaseline.baseline_value = 0;
    assertTrue("missing point with baseline_value rejected",
        !isPointModelValid(missingWithBaseline));

    PointPointsetModel missingWithRange = new PointPointsetModel();
    missingWithRange.type = "missing";
    missingWithRange.range_min = 0.0;
    assertTrue("missing point with range_min rejected", !isPointModelValid(missingWithRange));

    PointPointsetModel validExprOnly = new PointPointsetModel();
    validExprOnly.expr = "US-MTV_1:supply_air_flowrate_sensor";
    assertTrue("valid expr-only point with underscore device id",
        isPointModelValid(validExprOnly));

    PointPointsetModel validArithmeticExpr = new PointPointsetModel();
    validArithmeticExpr.expr =
        "VAV-3:supply_air_flowrate_sensor - VAV-1:supply_air_flowrate_sensor";
    assertTrue("valid arithmetic expr-only point", isPointModelValid(validArithmeticExpr));

    PointPointsetModel validExprWithUnits = new PointPointsetModel();
    validExprWithUnits.expr =
        "(AHU-1:supply_air_flowrate_sensor + AHU-2:supply_air_flowrate_sensor) / 2";
    validExprWithUnits.units = "Liters-per-second";
    assertTrue("valid arithmetic expr point with units", isPointModelValid(validExprWithUnits));

    PointPointsetModel blankExpr = new PointPointsetModel();
    blankExpr.expr = "   ";
    assertTrue("blank expr rejected", !isPointModelValid(blankExpr));

    PointPointsetModel exprOnlyWithBaseline = new PointPointsetModel();
    exprOnlyWithBaseline.expr = "AHU-1:supply_air_flowrate_sensor";
    exprOnlyWithBaseline.baseline_value = 10.0;
    assertTrue("expr-only point with baseline_value rejected",
        !isPointModelValid(exprOnlyWithBaseline));

    PointPointsetModel exprOnlyWithRange = new PointPointsetModel();
    exprOnlyWithRange.expr = "AHU-1:supply_air_flowrate_sensor";
    exprOnlyWithRange.range_min = 0.0;
    assertTrue("expr-only point with range_min rejected", !isPointModelValid(exprOnlyWithRange));
  }

  @Test
  public void missingAndExprPointsExcludedFromConfigTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute(() -> {
      LocalDevice device = registrar.getWorkingDevices().get(DEVICE_ID);
      Metadata metadata = device.getMetadata();

      PointPointsetModel missingPoint = new PointPointsetModel();
      missingPoint.type = "missing";
      metadata.pointset.points.put("missing_sensor", missingPoint);

      PointPointsetModel exprPoint = new PointPointsetModel();
      exprPoint.expr = "VAV-3:supply_air_flowrate_sensor - VAV-1:supply_air_flowrate_sensor";
      metadata.pointset.points.put("calculated_sensor", exprPoint);
    });

    LocalDevice device = registrar.getWorkingDevices().get(DEVICE_ID);
    udmi.schema.Config config =
        com.google.daq.mqtt.util.ConfigManager.configFrom(device.getMetadata()).deviceConfig();
    assertTrue("regular point remains in config",
        config.pointset.points.containsKey("filter_alarm_pressure_status"));
    assertTrue("missing point stripped from config",
        !config.pointset.points.containsKey("missing_sensor"));
    assertTrue("expr point stripped from config",
        !config.pointset.points.containsKey("calculated_sensor"));
  }

  @Test
  public void dboExternalProcessorTest() throws Exception {
    File tempOut = java.nio.file.Files.createTempDirectory("dbo_ext_test").toFile();
    tempOut.deleteOnExit();

    Metadata metadata = new Metadata();
    metadata.system = new udmi.schema.SystemModel();
    metadata.system.guid = "11111111-2222-3333-4444-555555555555";
    metadata.localnet = new LocalnetModel();
    metadata.localnet.families = new HashMap<>();
    FamilyLocalnetModel bacnet = new FamilyLocalnetModel();
    bacnet.name = "VAV-BAC-99";
    metadata.localnet.families.put("bacnet", bacnet);
    metadata.externals = new HashMap<>();
    udmi.schema.LinkExternalsModel dboLink = new udmi.schema.LinkExternalsModel();
    dboLink.type = ImmutableList.of("HVAC/VAV_SD", "HVAC/DMP");
    dboLink.label = "LEGACY-LABEL";
    metadata.externals.put("dbo", dboLink);

    com.google.udmi.util.SiteDevice siteDevice = new com.google.udmi.util.SiteDevice() {
      @Override
      public String getDeviceId() {
        return "VAV-99";
      }

      @Override
      public Metadata getMetadata() {
        return metadata;
      }

      @Override
      public File getOutDir() {
        return tempOut;
      }
    };

    new com.google.daq.mqtt.external.DboExternalProcessor(null).process(siteDevice);
    File dboFile = new File(tempOut, "building_config.json");
    com.google.daq.mqtt.external.BuildingConfig written =
        JsonUtil.loadFileRequired(com.google.daq.mqtt.external.BuildingConfig.class, dboFile);
    udmi.schema.BuildingConfigEntity entity =
        written.get("11111111-2222-3333-4444-555555555555");
    assertNotNull("DBO entity written", entity);
    assertEquals("prefers localnet.families.bacnet.name", "VAV-BAC-99", entity.code);
    assertEquals("preserves multi-type list",
        ImmutableList.of("HVAC/VAV_SD", "HVAC/DMP"), entity.type);
    dboFile.delete();
    tempOut.delete();
  }

  @Test
  public void deviceGuidValidationAndGetterTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute();
    LocalDevice sns4 = registrar.getWorkingDevices().get("SNS-4");
    assertEquals("0060917f-8c21-47c4-b011-52521085fdc3", sns4.getDeviceGuid());

    LocalDevice ahu1 = registrar.getWorkingDevices().get(DEVICE_ID);
    ahu1.getMetadata().system.guid = "NOT-A-UUID";
    try {
      ahu1.validateEnvelope(REGISTRY_ID, "ZZ-TRI-FECTA");
      fail("Expected invalid system.guid to fail envelope validation");
    } catch (IllegalStateException expected) {
      assertTrue(friendlyStackTrace(expected).contains("deviceGuid"));
    }
  }

  private boolean isPointModelValid(PointPointsetModel pointModel) {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute(() -> {
      Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
      Metadata metadata = localDevices.get(DEVICE_ID).getMetadata();
      metadata.pointset.points.put("test_typed_point", pointModel);
    });
    LocalDevice device = registrar.getWorkingDevices().get(DEVICE_ID);
    return !device.hasCategory(ExceptionCategory.settings);
  }

  @Test
  public void transitiveUpdate() {
    List<MockAction> baseMocked = executeRegistrarClean(ImmutableList.of(GATEWAY_ID));
    List<MockAction> baseCreate = filterActions(baseMocked, CREATE_DEVICE_ACTION);
    assertEquals("Devices created directly", 1, baseCreate.size());

    List<MockAction> transitiveMocked = executeRegistrarClean(ImmutableList.of("-T", GATEWAY_ID));
    List<MockAction> transitiveCreate = filterActions(transitiveMocked, CREATE_DEVICE_ACTION);
    assertEquals("Devices created transitively", 4, transitiveCreate.size());

    int transitiveCreates = transitiveCreate.stream()
        .filter(mock -> !mock.deviceId.equals(GATEWAY_ID)).toList().size();
    assertEquals("Transitive devices", 3, transitiveCreates);
  }

  @Test
  public void basicUpdates() {
    List<MockAction> mockActions = executeRegistrarPopulated(ImmutableList.of());

    List<MockAction> blockActions = filterActions(mockActions, BLOCK_DEVICE_ACTION);
    assertEquals("block action count", 0, blockActions.size());

    List<MockAction> createActions = filterActions(mockActions, CREATE_DEVICE_ACTION);
    assertEquals("Devices created", 2, createActions.size());

    List<MockAction> deleteActions = filterActions(mockActions, DELETE_DEVICE_ACTION);
    assertEquals("Devices deleted", 2, deleteActions.size());

    List<MockAction> updateActions = filterActions(mockActions, UPDATE_DEVICE_ACTION);
    assertEquals("Devices updated", 4, updateActions.size());

    assertTrue("all devices not blocked", updateActions.stream().allMatch(this::isNotBlocking));
    List<MockAction> bindActions = filterActions(mockActions, BIND_DEVICE_ACTION);
    assertEquals("bind actions", 1, bindActions.size());
    assertTrue("bind gateway",
        bindActions.stream().allMatch(action -> action.data.equals("GAT-123")));
    assertEquals("bind devices", ImmutableSet.of("AHU-22"),
        bindActions.stream().map(action -> action.deviceId).collect(
            Collectors.toSet()));
  }

  @Test
  public void scanAddrMismatchHandlingTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute(() -> {
      Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
      LocalDevice device = localDevices.get(DEVICE_ID);
      Metadata metadata = device.getMetadata();
      if (metadata.localnet == null) {
        metadata.localnet = new LocalnetModel();
      }
      if (metadata.localnet.families == null) {
        metadata.localnet.families = new HashMap<>();
      }
      FamilyLocalnetModel familyModel = new FamilyLocalnetModel();
      familyModel.addr = "2C:58:B9:6C:7A:88"; // Uppercase MAC address
      metadata.localnet.families.put("ether", familyModel);
    });

    LocalDevice device = registrar.getWorkingDevices().get(DEVICE_ID);
    assertNotNull("Generated config should exist despite scan_addr validation error",
        device.getSettings().config);
    assertTrue("Device should record schema violation for scan_addr mismatch",
        device.hasCategory(ExceptionCategory.schema));
  }

  @Test
  public void invalidGatewayIdTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute(() -> {
      Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
      localDevices.get("SNS-8").getMetadata().gateway.gateway_id = "GAT-100";
    });

    LocalDevice device = registrar.getWorkingDevices().get("SNS-8");
    assertTrue("Device should record proxy error for nonexistent gateway_id",
        device.hasCategory(ExceptionCategory.proxy));
  }

  @Test
  public void localnetWithoutFamiliesTest() {
    Registrar registrar = getRegistrar(ImmutableList.of());
    registrar.execute(() -> {
      LocalnetModel parentOnly = new LocalnetModel();
      parentOnly.parent = new Parent();
      parentOnly.parent.target = GATEWAY_ID;
      parentOnly.parent.family = "hardwired";
      Map<String, LocalDevice> localDevices = registrar.getWorkingDevices();
      localDevices.get("SNS-8").getMetadata().localnet = parentOnly;
      localDevices.get(GATEWAY_ID).getMetadata().localnet.families = null;
    });

    for (String deviceId : ImmutableList.of("SNS-8", GATEWAY_ID)) {
      LocalDevice device = registrar.getWorkingDevices().get(deviceId);
      assertEquals(deviceId + " should have no errors", Set.of(), device.getTreeChildren());
      assertNotNull(deviceId + " generated config should exist", device.getSettings().config);
      assertNull(deviceId + " generated config.localnet should be null when families is omitted",
          device.deviceConfigObject().localnet);
    }
  }

  private Boolean isNotBlocking(MockAction action) {
    return !TRUE.equals(((CloudModel) action.data).blocked);
  }

  private List<MockAction> filterActions(List<MockAction> mockActions, ActionType actionKey) {
    return mockActions.stream()
        .filter(action -> action.action.equals(actionKey))
        .collect(Collectors.toList());
  }

  private List<MockAction> executeRegistrarPopulated(ImmutableList<String> optArgs) {
    return executeRegistrar(false, optArgs);
  }

  private List<MockAction> executeRegistrarClean(ImmutableList<String> optArgs) {
    return executeRegistrar(true, optArgs);
  }

  @NotNull
  private List<MockAction> executeRegistrar(boolean clean, ImmutableList<String> optArgs) {
    Registrar registrar = getRegistrar(clean, optArgs);
    registrar.execute();
    return registrar.getMockActions();
  }
}
