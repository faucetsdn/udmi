package com.google.daq.mqtt.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.everit.json.schema.ValidationException;

public class ExceptionMap extends RuntimeException {

  private static final byte[] NEWLINE_BYTES = "\n".getBytes();
  private static final byte[] SEPARATOR_BYTES = ": ".getBytes();

  final Map<String, Exception> exceptions = new TreeMap<>();

  public ExceptionMap(String description) {
    super(description);
  }

  private void forEach(BiConsumer<String, Exception> consumer) {
    exceptions.forEach(consumer);
  }

  public boolean isEmpty() {
    return exceptions.isEmpty();
  }

  public void throwIfNotEmpty() {
    if (!exceptions.isEmpty() || getCause() != null) {
      throw this;
    }
  }

  public void put(String key, Exception exception) {
    if (exceptions.put(key, exception) != null) {
      throw new IllegalArgumentException("Exception key already defined: " + key);
    }
  }

  public static ErrorTree format(Exception e, String indent) {
    return format(e, "", indent);
  }

  private static ErrorTree format(Throwable e, final String prefix, final String indent) {
    final ErrorTree errorTree = new ErrorTree();
    errorTree.prefix = prefix;
    errorTree.message = e.getMessage();
    final String newPrefix = prefix + indent;
    if (e instanceof ExceptionMap) {
      if (e.getCause() != null) {
        errorTree.child = format(e.getCause(), newPrefix, indent);
      }
      ((ExceptionMap) e).forEach(
          (key, sub) -> errorTree.children.put(key, format(sub, newPrefix, indent)));
    } else if (e instanceof ValidationException) {
      ((ValidationException) e).getCausingExceptions().forEach(
          sub -> errorTree.children.put(sub.getMessage(), format(sub, newPrefix, indent)));
    } else if (e.getCause() != null) {
      errorTree.child = format(e.getCause(), newPrefix, indent);
    }
    if (errorTree.children.isEmpty()) {
      errorTree.children = null;
    }
    if (errorTree.child == null && errorTree.children == null && errorTree.message == null) {
      errorTree.message = e.toString();
    }
    return errorTree;
  }

  public Stream<Map.Entry<String, Exception>> stream() {
    return exceptions.entrySet().stream();
  }

  public int size() {
    return exceptions.size();
  }

  public static class ErrorTree {
    public String prefix;
    public String message;
    public ErrorTree child;
    public Map<String, ErrorTree> children = new TreeMap<>();

    public void write(PrintStream err) {
      if (message == null && children == null && child == null) {
        throw new RuntimeException("Empty ErrorTree object");
      }
      try {
        if (message != null) {
          err.write(prefix.getBytes());
          err.write(message.getBytes());
          err.write(NEWLINE_BYTES);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (child != null) {
        child.write(err);
      }
      if (children != null) {
        children.forEach((key, value) -> value.write(err));
      }
    }
  }

}
