package com.google.daq.mqtt.util;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;

import static com.google.daq.mqtt.util.ConfigUtil.readCloudIotConfig;

public class PubSubPusher {

  private final Publisher publisher;
  private final String registrar_topic;

  {
    // Why this needs to be done there is no rhyme or reason.
    LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
  }

  public PubSubPusher(String projectId, File iotConfigFile) {
    try {
      CloudIotConfig cloudIotConfig = validate(readCloudIotConfig(iotConfigFile));
      registrar_topic = cloudIotConfig.registrar_topic;
      ProjectTopicName topicName = ProjectTopicName.of(projectId, registrar_topic);
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
      throw new RuntimeException("While sending to topic " + registrar_topic, e);
    }
  }

  public void shutdown() {
    try {
      publisher.publishAllOutstanding();
      publisher.shutdown();
      System.err.println("Done with PubSubPusher");
    } catch (Exception e) {
      throw new RuntimeException("While shutting down publisher" + registrar_topic, e);
    }
  }

  private CloudIotConfig validate(CloudIotConfig readCloudIotConfig) {
    Preconditions.checkNotNull(readCloudIotConfig.registrar_topic, "registrar_topic not defined");
    return readCloudIotConfig;
  }
}
