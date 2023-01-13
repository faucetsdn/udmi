package com.google.daq.mqtt.sequencer;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.WebServerRunner;
import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.daq.mqtt.util.Common;
import com.google.udmi.util.SiteModel;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
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
  static ExecutionConfiguration executionConfiguration;
  private static final Set<String> failures = new TreeSet<>();
  private static final Set<String> allTests = new TreeSet<>();
  private final Set<String> sequenceClasses = Common.allClassesInPackage(ConfigSequences.class);
  PrintStream githubStepSummaryFile;

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
    sequenceRunner.process(targets);
    return sequenceRunner.resultCode();
  }

  private static SequenceRunner processConfig(ExecutionConfiguration config) {
    executionConfiguration = config;
    SequenceRunner sequenceRunner = new SequenceRunner();
    SequenceBase.setDeviceId(config.device_id);
    sequenceRunner.process(List.of());
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

  private void openGithubStepSummary() {
    String filename = System.getenv("GITHUB_STEP_SUMMARY");
    if (filename != null) {
      try {
        githubStepSummaryFile = new PrintStream(filename);
      } catch (FileNotFoundException e) {
        return;
      }
    }
  }

  private void closeGithubStepSummary() {
    if (githubStepSummaryFile != null) {
      githubStepSummaryFile.close();
    }
  }

  private void outputGithubStepSummary(String name, String condition) {
    if (githubStepSummaryFile != null) {
      githubStepSummaryFile.println("| " + name + " | " + condition + "|\n");
    }
  }

  private void process(List<String> targetMethods) {
    if (sequenceClasses.isEmpty()) {
      throw new RuntimeException("No testing classes found");
    }
    System.err.println("Target sequence classes:\n  " + Joiner.on("\n  ").join(sequenceClasses));
    SequenceBase.ensureValidatorConfig();
    String deviceId = SequenceBase.validatorConfig.device_id;
    Set<String> remainingMethods = new HashSet<>(targetMethods);
    int runCount = 0;
    for (String className : sequenceClasses) {
      Class<?> clazz = Common.classForName(className);
      List<Request> requests = new ArrayList<>();
      if (!targetMethods.isEmpty()) {
        for (String method : targetMethods) {
          try {
            clazz.getMethod(method);
          } catch (Exception e) {
            continue;
          }
          System.err.println("Running " + clazz + "#" + method);
          requests.add(Request.method(clazz, method));
          remainingMethods.remove(method);
        }
      } else {
        System.err.println("Running " + clazz + " (all tests)");
        requests.add(Request.aClass(clazz));
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

    openGithubStepSummary();

    allTests.forEach(testName -> {
      String result = failures.contains(testName) ? "FAIL" : "PASS";
      System.err.printf("%s %s%n", result, testName);
      outputGithubStepSummary(testName, result);
    });

    closeGithubStepSummary();
  }

}
