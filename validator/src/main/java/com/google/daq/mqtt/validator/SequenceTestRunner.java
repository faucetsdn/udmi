package com.google.daq.mqtt.validator;

import java.util.ArrayList;
import java.util.List;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Custom test runner that can execute a specific method to test.
 */
public class SequenceTestRunner {

  private static final String INITIALIZATION_ERROR_PREFIX = "initializationError(org.junit.";
  private static final int EXIT_STATUS_SUCCESS = 0;
  private static final int EXIST_STATUS_FAILURE = 1;
  private static final int EXIT_STATUS_NO_TESTS = 2;

  /**
   * Thundercats go!
   * 
   * @param args Test classes/method to test.
   *
   * @throws ClassNotFoundException For bad test class
   */
  public static void main(String... args) throws ClassNotFoundException {
    int successes = 0;
    List<Failure> failures = new ArrayList<>();
    if (args.length == 0) {
      throw new IllegalArgumentException("Specify test class(#method) as command arguments");
    }
    for (String arg : args) {
      String[] classAndMethod = arg.split("#", 2);
      final Request request;
      if (classAndMethod.length > 1) {
        System.err.println("Running " + classAndMethod[1] + " from class " + classAndMethod[0]);
        request = Request.method(Class.forName(classAndMethod[0]),
            classAndMethod[1]);
      } else {
        System.err.println("Running all tests from class " + classAndMethod[0]);
        request = Request.aClass(Class.forName(classAndMethod[0]));
      }

      Result result = new JUnitCore().run(request);
      if (hasActualTestResults(result)) {
        failures.addAll(result.getFailures());
        successes += result.getRunCount() - failures.size();
      }
    }
    System.err.println("Test successes: " + successes);
    failures.forEach(failure -> {
      System.err.println(
          "Test failure: " + failure.getDescription().getMethodName() + " " + failure.getMessage());
    });
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
