package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.JsonUtil.JSON_SUFFIX;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SORTED;
import static java.util.Spliterators.spliteratorUnknownSize;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Traverse a directory structure and iterate (in order) over the actual files.
 */
public class DirectoryTraverser implements Iterator<File> {

  final List<File> files;
  File next;
  int nextIndex;
  DirectoryTraverser delegate;

  /**
   * New instance for the given directory path.
   */
  public DirectoryTraverser(String path) {
    File dir = new File(path);
    File[] listing = requireNonNull(dir.listFiles(), "missing directory " + dir.getAbsolutePath());
    Arrays.sort(listing);
    files =
        Arrays.stream(listing).filter(DirectoryTraverser::fileFilter).collect(Collectors.toList());
    if (files.isEmpty()) {
      throw new RuntimeException("No trace files found in " + dir.getAbsolutePath());
    }
    nextIndex = 0;
    next = prefetchNext();
  }

  private static boolean fileFilter(File file) {
    return file.isDirectory() || file.getName().endsWith(JSON_SUFFIX);
  }

  private File prefetchNext() {
    if (delegate != null) {
      if (delegate.hasNext()) {
        return delegate.next();
      }
      delegate = null;
    }

    if (nextIndex >= files.size()) {
      return null;
    }

    File next = files.get(nextIndex++);

    if (next.isDirectory()) {
      delegate = new DirectoryTraverser(next.getPath());
      return prefetchNext();
    }

    return next;
  }

  @Override
  public boolean hasNext() {
    return next != null;
  }

  @Override
  public File next() {
    File value = requireNonNull(next, "no more files available");
    next = prefetchNext();
    return value;
  }

  public Stream<File> stream() {
    int characteristics = ORDERED | DISTINCT | SORTED | NONNULL | IMMUTABLE;
    return StreamSupport.stream(spliteratorUnknownSize(this, characteristics), false);
  }
}
