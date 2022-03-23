package com.google.daq.mqtt.validator;

import java.util.ArrayList;
import java.util.List;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class SequenceTestRunner {

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
      failures.addAll(result.getFailures());
      successes += result.getRunCount() - failures.size();
    }
    System.err.println("Test successes: " + successes);
    failures.forEach(failure -> {
      System.err.println(
          "Test failure: " + failure.getDescription().getMethodName() + failure.getMessage());
    });
    System.exit(failures.isEmpty() ? 0 : 1);
  }
}
