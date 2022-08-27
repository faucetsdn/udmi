package com.google.daq.mqtt.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for managing a map of exceptions (named by category).
 */
public class ExceptionMap extends RuntimeException {

  private static final byte[] NEWLINE_BYTES = "\n".getBytes();
  private static final byte[] SEPARATOR_BYTES = ": ".getBytes();

  final Map<String, Exception> exceptions = new TreeMap<>();

  public ExceptionMap(String description) {
    super(description);
  }

  /**
   * Format the given exception with indicated level.
   *
   * @param e      exception (tree) to format
   * @param indent indent level
   * @return formatted error tree
   */
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
      ((ExceptionMap) e)
          .forEach((key, sub) -> errorTree.children.put(key, format(sub, newPrefix, indent)));
    } else if (e instanceof ValidationException) {
      ((ValidationException) e)
          .getCausingExceptions()
          .forEach(sub -> errorTree.children.put(sub.getMessage(), format(sub, newPrefix, indent)));
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

  private void forEach(BiConsumer<String, Exception> consumer) {
    exceptions.forEach(consumer);
  }

  public boolean isEmpty() {
    return exceptions.isEmpty();
  }

  /**
   * Throw an exception if the map is not empty, otherwise do nothing.
   */
  public void throwIfNotEmpty() {
    if (!exceptions.isEmpty() || getCause() != null) {
      throw this;
    }
  }

  /**
   * Put a new entry into the map.
   *
   * @param key       entry key
   * @param exception exception to add
   */
  public void put(String key, Exception exception) {
    if (exceptions.put(key, exception) != null) {
      throw new IllegalArgumentException("Exception key already defined: " + key);
    }
  }

  public Stream<Map.Entry<String, Exception>> stream() {
    return exceptions.entrySet().stream();
  }

  /**
   * Simple size getter.
   *
   * @return number of exceptions in the tree
   */
  public int size() {
    return exceptions.size();
  }

  /**
   * Tree of errors.
   */
  public static class ErrorTree {

    public String prefix;
    public String message;
    public ErrorTree child;
    public Map<String, ErrorTree> children = new TreeMap<>();

    /**
     * Render the tree as a string.
     *
     * @return rendered tree
     */
    public String asString() {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (PrintStream printStream = new PrintStream(byteArrayOutputStream)) {
        write(printStream);
      }
      return byteArrayOutputStream.toString();
    }

    /**
     * Write the tree to the output stream.
     *
     * @param err output stream to write
     */
    public void write(OutputStream err) {
      write(new PrintStream(err));
    }

    /**
     * Write the tree to the print stream.
     *
     * @param err output print stream
     */
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

    /**
     * Purge the indicated error patterns for the map.
     *
     * @param ignoreErrors list of patterns to purge
     * @return true if the resulting map is empty
     */
    public boolean purge(List<Pattern> ignoreErrors) {
      if (message == null) {
        return true;
      }
      if (ignoreErrors == null) {
        return (children == null || children.isEmpty()) && child == null;
      }
      if (ignoreErrors.stream().anyMatch(pattern -> pattern.matcher(message).find())) {
        return true;
      }
      if (child != null && child.purge(ignoreErrors)) {
        return true;
      }
      if (children != null) {
        children =
            children.entrySet().stream()
                .filter(entry -> !entry.getValue().purge(ignoreErrors))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        return children.isEmpty();
      }
      return false;
    }
  }
}
