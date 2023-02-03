package com.google.daq.mqtt.sequencer;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.WebServerRunner;
import com.google.daq.mqtt.sequencer.Feature.Stage;
import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.daq.mqtt.util.Common;
import com.google.udmi.util.SiteModel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import joptsimple.internal.Strings;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Level;

/**
 * Custom test runner that can execute a specific method to test.
 */
public class SequenceRunner {

  private static final int EXIT_STATUS_SUCCESS = 0;
  private static final int EXIST_STATUS_FAILURE = 1;
  public static final Stage DEFAULT_FEATURE_STAGE = Stage.BETA;
  static ExecutionConfiguration executionConfiguration;
  private static final Set<String> failures = new TreeSet<>();
  private static final Set<String> allTests = new TreeSet<>();
  private final Set<String> sequenceClasses = Common.allClassesInPackage(ConfigSequences.class);
  private List<String> targets = List.of();

  /**
   * Thundercats are go.
   *
   * @param args Test classes/method to test.
   */
  public static void main(String[] args) {
    System.exit(processResult(Arrays.asList(args)));
  }

  /**
   * Execute sequence tests.
   *
   * @param targets individual tests to run
   * @return status code
   */
  public static int processResult(List<String> targets) {
    SequenceRunner sequenceRunner = new SequenceRunner();
    sequenceRunner.setTargets(targets);
    sequenceRunner.process();
    return sequenceRunner.resultCode();
  }

  private static SequenceRunner processConfig(ExecutionConfiguration config) {
    executionConfiguration = config;
    SequenceRunner sequenceRunner = new SequenceRunner();
    SequenceBase.setDeviceId(config.device_id);
    sequenceRunner.process();
    return sequenceRunner;
  }

  /**
   * Handle a parameterized request to run a sequence on a device.
   *
   * @param params parameters for request
   */
  public static void handleRequest(Map<String, String> params) {
    final String sitePath = params.remove(WebServerRunner.SITE_PARAM);
    final String projectId = params.remove(WebServerRunner.PROJECT_PARAM);
    final String serialNo = params.remove(WebServerRunner.SERIAL_PARAM);
    final String deviceId = params.remove(WebServerRunner.DEVICE_PARAM);
    final String testMode = params.remove(WebServerRunner.TEST_PARAM);

    SiteModel siteModel = new SiteModel(sitePath);
    siteModel.initialize();

    ExecutionConfiguration config = new ExecutionConfiguration();
    config.project_id = projectId;
    config.site_model = sitePath;
    config.device_id = deviceId;
    config.key_file = siteModel.validatorKey();
    config.serial_no = Optional.ofNullable(serialNo).orElse(SequenceBase.SERIAL_NO_MISSING);
    config.log_level = Level.INFO.name();
    config.udmi_version = Common.getUdmiVersion();
    config.alt_project = testMode; // Sekrit hack for enabling mock components.

    failures.clear();
    allTests.clear();

    SequenceBase.resetState();

    if (deviceId != null) {
      SequenceRunner.processConfig(config);
    } else {
      siteModel.forEachDevice(device -> {
        config.device_id = device.deviceId;
        SequenceRunner.processConfig(config);
      });
    }
  }

  public static Set<String> getFailures() {
    return failures;
  }

  public static Set<String> getAllTests() {
    return allTests;
  }

  private int resultCode() {
    if (failures == null) {
      throw new RuntimeException("Sequences have not been processed");
    }
    return failures.isEmpty() ? EXIT_STATUS_SUCCESS : EXIST_STATUS_FAILURE;
  }

  private void process() {
    if (sequenceClasses.isEmpty()) {
      throw new RuntimeException("No testing classes found");
    }
    System.err.println("Target sequence classes:\n  " + Joiner.on("\n  ").join(sequenceClasses));
    SequenceBase.ensureValidatorConfig();
    String deviceId = SequenceBase.validatorConfig.device_id;
    Set<String> remainingMethods = new HashSet<>(targets);
    int runCount = 0;
    for (String className : sequenceClasses) {
      Class<?> clazz = Common.classForName(className);
      List<Request> requests = new ArrayList<>();
      List<String> runMethods = getRunMethods(clazz);
      for (String method : runMethods) {
        System.err.println("Running target " + clazz.getName() + "#" + method);
        requests.add(Request.method(clazz, method));
        remainingMethods.remove(method);
      }
      for (Request request : requests) {
        Result result = new JUnitCore().run(request);
        Set<String> failureNames = result.getFailures().stream()
            .map(failure -> deviceId + "/" + failure.getDescription().getMethodName()).collect(
                Collectors.toSet());
        failures.addAll(failureNames);
        runCount += result.getRunCount();
      }
    }

    if (!remainingMethods.isEmpty()) {
      throw new RuntimeException("Failed to find " + Joiner.on(", ").join(remainingMethods));
    }

    if (runCount <= 0) {
      throw new RuntimeException("No tests were executed!");
    }

    allTests.forEach(testName -> {
      String result = failures.contains(testName) ? "FAIL" : "PASS";
      System.err.printf("%s %s%n", result, testName);
    });
  }

  private List<String> getRunMethods(Class<?> clazz) {
    return Arrays.stream(clazz.getMethods()).filter(this::shouldProcessMethod).map(Method::getName)
        .filter(this::isTargetMethod).collect(Collectors.toList());
  }

  private boolean isTargetMethod(String methodName) {
    return targets.isEmpty() || targets.contains(methodName);
  }

  private boolean shouldProcessMethod(Method method) {
    Test test = method.getAnnotation(Test.class);
    if (test == null) {
      return false;
    }
    // If the target is explicitly indicated, then we should test it regardless of annotation.
    if (targets.contains(method.getName())) {
      return true;
    }
    Feature annotation = method.getAnnotation(Feature.class);
    Stage stage = annotation == null ? Feature.DEFAULT_STAGE : annotation.stage();
    return stage.processGiven(getFeatureStageLevel());
  }

  private Stage getFeatureStageLevel() {
    String stage = SequenceBase.validatorConfig.feature_stage;
    return Strings.isNullOrEmpty(stage) ? DEFAULT_FEATURE_STAGE : Stage.valueOf(stage);
  }

  public void setTargets(List<String> targets) {
    this.targets = targets;
  }
}
