package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class CommandLineProcessor {

  private static final String TERMINATING_ARG = "--";
  private static final String EQUALS_SIGN = "=";
  private static final int LINUX_SUCCESS_CODE = 0;
  private static final int LINUX_ERROR_CODE = -1;
  private final Object target;
  private final Method showHelpMethod;

  Map<CommandLineOption, Method> optionMap = new HashMap<>();

  public CommandLineProcessor(Object target) {
    this.target = target;

    showHelpMethod = getShowHelpMethod();
    optionMap.put(getShowHelpOption(), showHelpMethod);

    List<Method> methods = new ArrayList<>(List.of(target.getClass().getDeclaredMethods()));
    methods.forEach(method -> ifNotNullThen(method.getAnnotation(CommandLineOption.class),
        a -> optionMap.put(a, method)));
    optionMap.values().forEach(method -> method.setAccessible(true));
  }

  private Method getShowHelpMethod() {
    try {
      return CommandLineProcessor.class.getDeclaredMethod("showUsage");
    } catch (Exception e) {
      throw new RuntimeException("While getting showHelp method", e);
    }
  }

  private CommandLineOption getShowHelpOption() {
    return showHelpMethod.getAnnotation(CommandLineOption.class);
  }

  @CommandLineOption(short_form = "-h")
  private void showUsage() {
    showUsage(null);
  }

  private void showUsage(String message) {
    ifNotNullThen(message, m -> System.err.println(m));
    System.err.println("Options supported:");
    optionMap.forEach((option, method) ->
        System.err.printf("  %s: %s%n", option.short_form(), option.long_form()));
    System.exit(message == null ? LINUX_SUCCESS_CODE : LINUX_ERROR_CODE);
  }

  public List<String> processArgs(List<String> argList) {
    try {
      while (!argList.isEmpty()) {
        String arg = argList.remove(0);
        if (arg.equals(TERMINATING_ARG)) {
          return argList;
        }
        Optional<Entry<CommandLineOption, Method>> first = optionMap.entrySet().stream()
            .filter(option -> processArgEntry(arg, option.getKey(), option.getValue(), argList))
            .findFirst();
        if (first.isEmpty()) {
          throw new IllegalStateException("Unrecognized command line option '" + arg + "'");
        }
      }
      return null;
    } catch (Exception e) {
      showUsage(e.getMessage());
      return null;
    }
  }

  private boolean processArgEntry(String arg, CommandLineOption option, Method method,
      List<String> argList) {
    try {
      if (arg.equals(option.short_form())) {
        if (method.equals(showHelpMethod)) {
          showUsage();
        } else if (requiresArg(method)) {
          String parameter = argList.remove(0);
          method.invoke(target, parameter);
        } else {
          method.invoke(target);
        }
        return true;
      } else if (!option.long_form().isBlank() && arg.startsWith(option.long_form())) {
        throw new RuntimeException("Long form command line not yet supported");
      } else if (option.short_form().isBlank() && option.long_form().isBlank()) {
        throw new RuntimeException(
            "Neither long nor short form not defined for " + method.getName());
      }
      return false;
    } catch (Exception e) {
      throw new RuntimeException("Processing command line method " + method.getName(), e);
    }
  }

  private void cleanExit(boolean success) {
    System.exit(success ? LINUX_SUCCESS_CODE : LINUX_ERROR_CODE);
  }

  private boolean requiresArg(Method method) {
    Type[] genericParameterTypes = method.getGenericParameterTypes();
    checkState(genericParameterTypes.length <= 1,
        "expected <= 1 parameter for command line method %s", method.getName());
    return genericParameterTypes.length == 1;
  }
}