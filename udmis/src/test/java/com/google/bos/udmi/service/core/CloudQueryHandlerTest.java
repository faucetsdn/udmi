package com.google.bos.udmi.service.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudQuery;
import udmi.schema.Envelope;

class CloudQueryHandlerTest implements MessageContinuation {

  private final ControlProcessor controlProcessor = mock(ControlProcessor.class);
  private final CloudQueryHandler queryHandler = new CloudQueryHandler(controlProcessor);
  private final Envelope envelope = new Envelope();

  @Before
  public void setupMock() {
   when(controlProcessor.getContinuation(isNotNull())).thenReturn(this);
  }

  @Override
  public Envelope getEnvelope() {
    return envelope;
  }

  @Override
  public void publish(Object message) {
    System.err.println(message);
  }

  @Test
  public void queryAllRegistries() {
    CloudQuery query = new CloudQuery();
    queryHandler.process(query);
  }
}
