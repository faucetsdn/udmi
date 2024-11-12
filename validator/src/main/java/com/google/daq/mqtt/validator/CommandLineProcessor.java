package com.google.daq.mqtt.validator;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

public class CommandLineProcessor {

  private static final String TERMINATING_ARG = "--";
  private static final String EQUALS_SIGN = "=";
  private final Object target

  Map<CommandLineOption, Method> optionMap = new HashMap<>();

  public CommandLineProcessor(Object target, Consumer<Exception> usage) {
    this.target = target;

    List<Method> methods = new ArrayList<>(List.of(target.getClass().getMethods()));

    methods.forEach(method -> ifNotNullThen(method.getAnnotation(CommandLineOption.class),
        a -> optionMap.put(a, method)));
  }

  public List<String> processArgs(List<String> argList) {
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
  }

  private boolean processArgEntry(String arg, CommandLineOption option, Method method,
      List<String> argList) {
    if (arg.equals(option.option())) {
      if (requiresArg(method)) {
        String arg = argList.remove(0);
        method.invoke(target, arg);
      }
      return true;
    } else if (arg.startsWith(option.long_form())) {

    }
    return false;
  }

  private boolean requiresArg(Method method) {
  }
}