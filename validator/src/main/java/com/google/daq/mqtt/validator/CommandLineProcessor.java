package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static java.lang.String.CASE_INSENSITIVE_ORDER;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Simple command line parser class.
 */
public class CommandLineProcessor {

  private static final String TERMINATING_ARG = "--";
  private static final String EQUALS_SIGN = "=";
  private static final int LINUX_SUCCESS_CODE = 0;
  private static final int LINUX_ERROR_CODE = -1;
  private final Object target;
  private final Method showHelpMethod;

  Map<CommandLineOption, Method> optionMap = new TreeMap<>(
      (a, b) -> CASE_INSENSITIVE_ORDER.compare(getSortArg(a), getSortArg(b)));

  private static String getSortArg(CommandLineOption k) {
    String shortForm = k.short_form();
    return shortForm.isBlank() ? k.long_form().substring(1, 2) : shortForm.substring(1, 2);
  }

  /**
   * Create a new command line processor for the given target object.
   */
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

  @CommandLineOption(short_form = "-h", description = "Show help and exit")
  private void showUsage() {
    showUsage(null);
  }

  /**
   * Show program usage.
   */
  public void showUsage(String message) {
    ifNotNullThen(message, m -> System.err.println(m));
    System.err.println("Options supported:");
    optionMap.forEach((option, method) -> System.err.printf("  %s %1s  %s%n",
        option.short_form(), option.arg_type(), option.description()));
    System.exit(message == null ? LINUX_SUCCESS_CODE : LINUX_ERROR_CODE);
  }

  /**
   * Process the given arg list. Return a list of remaining arguments (if any).
   */
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
          throw new IllegalArgumentException("Unrecognized command line option '" + arg + "'");
        }
        if (!arg.startsWith("-")) {
          argList.add(0, arg);
          return argList;
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
          checkState(!option.arg_type().isBlank(), "Option with argument missing type parameter");
          String parameter = argList.remove(0);
          method.invoke(target, parameter);
        } else {
          method.invoke(target);
        }
        return true;
      } else if (!option.long_form().isBlank() && arg.startsWith(option.long_form())) {
        throw new IllegalArgumentException("Long form command line not yet supported");
      } else if (option.short_form().isBlank() && option.long_form().isBlank()) {
        throw new IllegalArgumentException(
            "Neither long nor short form not defined for " + method.getName());
      } else if (!arg.startsWith("-")) {
        return true;
      }
      return false;
    } catch (Exception e) {
      throw new IllegalArgumentException("Processing command line method " + method.getName(), e);
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