package com.google.daq.mqtt.sequencer;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.WebServerRunner;
import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.daq.mqtt.util.Common;
import com.google.daq.mqtt.util.ValidatorConfig;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import udmi.schema.Level;

/**
 * Custom test runner that can execute a specific method to test.
 */
public class SequenceTestRunner {

  private static final String INITIALIZATION_ERROR_PREFIX = "initializationError(org.junit.";
  private static final int EXIT_STATUS_SUCCESS = 0;
  private static final int EXIST_STATUS_FAILURE = 1;
  static ValidatorConfig validationConfig;
  private final Set<String> sequenceClasses = Common.findAllClassesUsingClassLoader(
      ConfigSequences.class.getPackageName());
  private int successes = -1;
  private List<Failure> failures;

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
    SequenceTestRunner sequenceRunner = new SequenceTestRunner();
    sequenceRunner.process(targets);
    return sequenceRunner.resultCode();
  }

  private static boolean hasActualTestResults(Result result) {
    return (result.getFailures().size() != 1
        || !result.getFailures().get(0).toString().startsWith(INITIALIZATION_ERROR_PREFIX));
  }

  static SequenceTestRunner processConfig(ValidatorConfig config) {
    validationConfig = config;
    SequenceTestRunner sequenceRunner = new SequenceTestRunner();
    sequenceRunner.process(List.of());
    return sequenceRunner;
  }

  /**
   * Handle a parameterized request to run a sequence on a device.
   *
   * @param params parameters for request
   */
  public static void handleRequest(Map<String, String> params) {
    String sitePath = params.remove(WebServerRunner.SITE_PARAM);
    String projectId = params.remove(WebServerRunner.PROJECT_PARAM);
    SiteModel siteModel = new SiteModel(sitePath);
    siteModel.initialize();
    String deviceId = params.remove(WebServerRunner.DEVICE_PARAM);

    ValidatorConfig config = new ValidatorConfig();
    config.project_id = projectId;
    config.site_model = sitePath;
    config.device_id = deviceId;
    config.key_file = siteModel.validatorKey();
    String serialNo = params.remove(WebServerRunner.SERIAL_PARAM);
    config.serial_no = Optional.ofNullable(serialNo).orElse(SequencesTestBase.SERIAL_NO_MISSING);
    config.log_level = Level.INFO.name();
    config.udmi_version = Common.getUdmiVersion();

    if (deviceId != null) {
      SequenceTestRunner.processConfig(config);
    } else {
      siteModel.forEachDevice(device -> {
        config.device_id = device.deviceId;
        SequenceTestRunner.processConfig(config);
      });
    }
  }

  private int resultCode() {
    if (successes < 0) {
      throw new RuntimeException("Sequences have not been processed");
    }
    return failures.isEmpty() ? EXIT_STATUS_SUCCESS : EXIST_STATUS_FAILURE;
  }

  public List<Failure> getFailures() {
    return failures;
  }

  private void process(List<String> targetMethods) {
    if (sequenceClasses.isEmpty()) {
      throw new RuntimeException("No testing classes found");
    }
    System.err.println("Target sequence classes:\n  " + Joiner.on("\n  ").join(sequenceClasses));
    successes = 0;
    failures = new ArrayList<>();
    Set<String> remainingMethods = new HashSet<>(targetMethods);
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
        if (hasActualTestResults(result)) {
          failures.addAll(result.getFailures());
          successes += result.getRunCount() - result.getFailureCount();
        }
      }
    }
    System.err.println("Test successes: " + successes);
    failures.forEach(failure -> {
      System.err.println(
          "Test failure: " + failure.getDescription().getMethodName() + " "
              + failure.getMessage());
      Throwable exception = failure.getException();
      if (exception != null) {
        exception.printStackTrace();
      }
    });
    if (!remainingMethods.isEmpty()) {
      throw new RuntimeException("Failed to find " + Joiner.on(", ").join(remainingMethods));
    }
    if (successes == 0 && failures.isEmpty()) {
      throw new RuntimeException("No matching tests found");
    }
  }

}
