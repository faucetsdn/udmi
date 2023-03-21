package com.google.bos.udmi.service.messaging;

import com.google.bos.udmi.service.pod.ComponentBase;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

public abstract class MessageBase extends ComponentBase implements MessagePipe {
  ExecutorService executor = Executors.newSingleThreadExecutor();
  private BlockingQueue<Bundle> loopQueue;
  private Consumer<Bundle> handler;

  public static Bundle getBundle(Object message, Map<String,String> attributes) {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.attributes = attributes;
    return bundle;
  }

  public void registerHandler(Consumer<Bundle> handler, SubType type, SubFolder folder) {
    this.handler = handler;
  }

  protected void processQueue(BlockingQueue<Bundle> queue) {
    loopQueue = queue;
    executor.submit(this::messageLoop);
  }

  private void messageLoop() {
    try {
      while (true) {
        Bundle bundle = loopQueue.take();
        if (bundle.attributes == null) {
          return;
        }
        processMessage(bundle);
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing message loop", e);
    }
  }

  private void processMessage(Bundle bundle) {
    handler.accept(bundle);
  }
}
