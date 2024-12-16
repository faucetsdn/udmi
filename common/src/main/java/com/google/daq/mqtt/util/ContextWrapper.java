package com.google.daq.mqtt.util;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

public class ContextWrapper {

  private static final ThreadLocal<List<String>> contexts = ThreadLocal.withInitial(ArrayList::new);

  public static <T> T runInContext(String context, Supplier<T> supplier) {
    contexts.get().add(context);
    try {
      return supplier.get();
    } catch (Exception e) {
      throw wrapExceptionWithContext(e, true);
    } finally {
      contexts.get().remove(contexts.get().size() - 1);
    }
  }

  public static void runInContext(String context, Runnable action) {
    runInContext(context, () -> {
      action.run();
      return null;
    });
  }

  public static String getCurrentContext() {
    List<String> contextList = contexts.get();
    return String.join(" -> ", contextList);
  }

  public static RuntimeException wrapExceptionWithContext(Exception e, boolean includeOnlyLatest) {
    List<String> contextStrings = contexts.get();
    if (contextStrings.size() == 0) {
      return new RuntimeException(e);
    }

    RuntimeException wrappedException = new RuntimeException(
        contextStrings.get(contextStrings.size() - 1), e);
    if (!includeOnlyLatest) {
      for (int i = contextStrings.size() - 2; i >= 0; i--) {
        String context = contextStrings.get(i);
        wrappedException = new RuntimeException(context, wrappedException);
      }
    }
    return wrappedException;
  }
}
