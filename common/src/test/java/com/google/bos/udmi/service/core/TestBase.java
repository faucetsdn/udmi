package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.MessageBase;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

public class TestBase extends TestCase {

  protected static AtomicInteger instanceCount = new AtomicInteger();

  protected int getExceptionCount() {
    LocalMessagePipe messagePipe = LocalMessagePipe.getPipeForNamespace(getNamespace());
    Map<String, AtomicInteger> handlerCounts = messagePipe.handlerCounts;
    return handlerCounts.getOrDefault(MessageBase.EXCEPTION_HANDLER, new AtomicInteger()).get();
  }

  protected int getDefaultCount() {
    LocalMessagePipe messagePipe = LocalMessagePipe.getPipeForNamespace(getNamespace());
    Map<String, AtomicInteger> handlerCounts = messagePipe.handlerCounts;
    return handlerCounts.getOrDefault(MessageBase.DEFAULT_HANDLER, new AtomicInteger()).get();
  }

  protected void drainPipe() {
    LocalMessagePipe.getPipeForNamespace(getNamespace()).drainSource();
  }

  protected String getNamespace() {
    return this.getClass().getSimpleName() + "#" + instanceCount.get();
  }
}
