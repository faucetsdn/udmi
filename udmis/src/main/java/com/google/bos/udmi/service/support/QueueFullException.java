package com.google.bos.udmi.service.support;

/**
 * Exception thrown when the bounded dynamic security command queue is full.
 */
public class QueueFullException extends RuntimeException {
  public QueueFullException(String message) {
    super(message);
  }
}
