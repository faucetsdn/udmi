package com.google.udmi.util;

import java.io.IOException;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

/**
 * An OutputStream that duplicates output to two output streams, useful for scenarios such as
 * logging to two destinations simultaneously.
 */
public class DualOutputStream extends OutputStream {

  private final OutputStream primary;
  private final OutputStream secondary;

  public DualOutputStream(OutputStream primary, OutputStream secondary) {
    this.primary = primary;
    this.secondary = secondary;
  }

  @Override
  public void write(int i) throws IOException {
    primary.write(i);
    secondary.write(i);
  }

  @Override
  public void write(byte @NotNull [] b) throws IOException {
    primary.write(b);
    secondary.write(b);
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) throws IOException {
    primary.write(b, off, len);
    secondary.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    primary.flush();
    secondary.flush();
  }

  @Override
  public void close() throws IOException {
    primary.close();
    secondary.close();
  }
}
