package com.google.bos.udmi.service.messaging;

import com.google.bos.udmi.service.pod.ComponentBase;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import udmi.schema.Envelope;

public abstract class MessageBase extends ComponentBase implements MessagePipe {

  public static final Envelope LOOP_EXIT_MARK = null;
  ExecutorService executor = Executors.newSingleThreadExecutor();
  private BlockingQueue<Bundle> loopQueue;
  private MessageHandler<Object> handler;

  public static Bundle makeBundle(Envelope envelope, Object message) {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = envelope;
    return bundle;
  }

  public static class Bundle {
    public Envelope envelope;
    public Object message;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void registerHandler(Class<T> targetClass, MessageHandler<T> handler) {
    this.handler = (MessageHandler<Object>) handler;
  }

  protected void processQueue(BlockingQueue<Bundle> queue) {
    loopQueue = queue;
    executor.submit(this::messageLoop);
  }

  private void messageLoop() {
    try {
      while (true) {
        Bundle bundle = loopQueue.take();
        // Lack of envelope can only happen intentionally as a signal to exist the loop.
        if (bundle.envelope == LOOP_EXIT_MARK) {
          return;
        }
        processMessage(bundle);
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing message loop", e);
    }
  }

  private void processMessage(Bundle bundle) {
    handler.accept(bundle.envelope, bundle.message);
  }
}
