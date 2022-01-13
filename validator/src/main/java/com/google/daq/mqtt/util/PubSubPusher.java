package com.google.daq.mqtt.util;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

public class PubSubPusher {

  private final Publisher publisher;
  private final String outTopic;

  public PubSubPusher(String projectId, String outTopic) {
    try {
      Preconditions.checkNotNull(projectId, "PubSub projectId");
      Preconditions.checkNotNull(outTopic, "PubSub publish topic");
      this.outTopic = outTopic;
      ProjectTopicName topicName = ProjectTopicName.of(projectId, outTopic);
      publisher = Publisher.newBuilder(topicName).build();
    } catch (Exception e) {
      throw new RuntimeException("While creating PubSubPublisher", e);
    }
  }

  public String sendMessage(Map<String, String> attributes, String body) {
    try {
      PubsubMessage message = PubsubMessage.newBuilder()
          .setData(ByteString.copyFrom(body, Charset.defaultCharset()))
          .putAllAttributes(attributes)
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      return publish.get();
    } catch (Exception e) {
      throw new RuntimeException("While sending to topic " + outTopic, e);
    }
  }

  public void shutdown() {
    try {
      publisher.publishAllOutstanding();
      publisher.shutdown();
      System.err.println("Done with PubSubPusher");
    } catch (Exception e) {
      throw new RuntimeException("While shutting down publisher" + outTopic, e);
    }
  }
}
