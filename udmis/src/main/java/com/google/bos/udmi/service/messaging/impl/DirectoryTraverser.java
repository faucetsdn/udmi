package com.google.bos.udmi.service.messaging.impl;

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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Traverse a directory structure and iterate (in order) over the actual files.
 */
public class DirectoryTraverser implements Iterator<File> {

  final File[] files;
  File next;
  int nextIndex;
  DirectoryTraverser delegate;

  /**
   * New instance for the given directory path.
   */
  public DirectoryTraverser(String path) {
    File file = new File(path);
    files = requireNonNull(file.listFiles(), "missing directory " + file.getAbsolutePath());
    Arrays.sort(files);
    nextIndex = 0;
    next = prefetchNext();
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

  private File prefetchNext() {
    if (delegate != null) {
      if (delegate.hasNext()) {
        return delegate.next();
      }
      delegate = null;
    }

    if (nextIndex >= files.length) {
      return null;
    }

    File next = files[nextIndex++];

    if (next.isDirectory()) {
      delegate = new DirectoryTraverser(next.getPath());
      return prefetchNext();
    }

    return next;
  }

  public Stream<File> stream() {
    int characteristics = ORDERED | DISTINCT | SORTED | NONNULL | IMMUTABLE;
    return StreamSupport.stream(spliteratorUnknownSize(this, characteristics), false);
  }
}
