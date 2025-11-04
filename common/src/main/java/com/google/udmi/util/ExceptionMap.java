package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.multiTrim;

import com.google.udmi.util.ErrorMap.ErrorMapException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for managing a map of exceptions (named by category).
 */
public class ExceptionMap extends RuntimeException {

  private static final byte[] NEWLINE_BYTES = "\n".getBytes();
  private static final byte[] SEPARATOR_BYTES = ": ".getBytes();
  private static final String ERROR_FORMAT_INDENT = "  ";

  final Map<ExceptionCategory, Exception> exceptions = new TreeMap<>();

  public ExceptionMap(String description) {
    super(description);
  }

  public enum ExceptionCategory {
    missing, extra, out, validation, loading, writing, site_metadata, initializing, sample,
    registering, envelope, credentials, samples, files, binding, creating, updating, schema,
    configuring, metadata, settings, status, preprocess, externals, proxy
  }

  /**
   * Format the given exception with indicated level.
   *
   * @param e exception (tree) to format
   * @return formatted error tree
   */
  public static ErrorTree format(Exception e) {
    return format(e, "", ERROR_FORMAT_INDENT);
  }

  private static ErrorTree format(Throwable e, final String prefix, final String indent) {
    final ErrorTree errorTree = new ErrorTree();
    errorTree.prefix = prefix;
    errorTree.message = multiTrim(e.getMessage());
    final String newPrefix = prefix + indent;
    if (e instanceof ExceptionMap exceptionMap) {
      if (e.getCause() != null) {
        errorTree.child = format(e.getCause(), newPrefix, indent);
      }
      exceptionMap.forEach((key, sub) ->
          errorTree.children.put(key.toString(), format(sub, newPrefix, indent)));
    } else if (e instanceof ErrorMapException errorMap) {
      errorMap.getMap().forEach((key, sub) ->
          errorTree.children.put(key, format(sub, newPrefix, indent)));
    } else if (e instanceof ValidationException) {
      ((ValidationException) e)
          .getCausingExceptions()
          .forEach(sub -> errorTree.children.put(multiTrim(sub.getMessage()),
              format(sub, newPrefix, indent)));
    } else if (e instanceof ExceptionList exceptionList) {
      exceptionList.exceptions().forEach(sub -> {
        errorTree.children.put(sub.getMessage(), format(sub, newPrefix, indent));
      });
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

  private void forEach(BiConsumer<ExceptionCategory, Exception> consumer) {
    exceptions.forEach(consumer);
  }

  public void forEach(Consumer<Exception> handler) {
    exceptions.values().forEach(handler);
  }

  public boolean isEmpty() {
    return exceptions.isEmpty();
  }

  public boolean hasCategory(ExceptionCategory category) {
    return exceptions.containsKey(category);
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
  public void put(ExceptionCategory key, Exception exception) {
    Exception previous = exceptions.put(key, exception);
    if (previous instanceof ExceptionList exceptionList) {
      exceptionList.exceptions().add(exception);
    } else if (previous != null) {
      exceptions.put(key, new ExceptionList(List.of(previous, exception)));
    }
  }

  public Stream<Map.Entry<ExceptionCategory, Exception>> stream() {
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
   * Execute the action and capture into the map if it throws an exception.
   */
  public void capture(ExceptionCategory category, Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      put(category, e);
    }
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
