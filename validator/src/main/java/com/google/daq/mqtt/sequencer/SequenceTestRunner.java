package com.google.daq.mqtt.sequencer;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import com.google.daq.mqtt.sequencer.sequences.DiscoverySequences;
import com.google.daq.mqtt.sequencer.sequences.WritebackSequences;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Custom test runner that can execute a specific method to test.
 */
public class SequenceTestRunner {

  private static final Set<Class<?>> sequenceClasses = ImmutableSet.of(
      ConfigSequences.class,
      DiscoverySequences.class,
      WritebackSequences.class);

  private static final String INITIALIZATION_ERROR_PREFIX = "initializationError(org.junit.";
  private static final int EXIT_STATUS_SUCCESS = 0;
  private static final int EXIST_STATUS_FAILURE = 1;
  private static final int EXIT_STATUS_NO_TESTS = 2;

  /**
   * Thundercats are go.
   *
   * @param args Test classes/method to test.
   *
   * @throws ClassNotFoundException For bad test class
   */
  public static void main(String... args) throws ClassNotFoundException {
    int successes = 0;
    List<Failure> failures = new ArrayList<>();
    Set<String> remainingMethods = new HashSet<>(Arrays.asList(args));
    for (Class<?> clazz : sequenceClasses) {
      List<Request> requests = new ArrayList<>();
      if (args.length > 0) {
        for (String method : args) {
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
            successes += result.getRunCount() - failures.size();
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
      System.err.println("Failed to find methods " + Joiner.on(", ").join(remainingMethods));
      System.exit(EXIT_STATUS_NO_TESTS);
    }
    if (successes == 0 && failures.isEmpty()) {
      System.err.println("No matching tests found");
      System.exit(EXIT_STATUS_NO_TESTS);
    }
    System.exit(failures.isEmpty() ? EXIT_STATUS_SUCCESS : EXIST_STATUS_FAILURE);
  }

  private static boolean hasActualTestResults(Result result) {
    return (result.getFailures().size() != 1
        || !result.getFailures().get(0).toString().startsWith(INITIALIZATION_ERROR_PREFIX));
  }
}
