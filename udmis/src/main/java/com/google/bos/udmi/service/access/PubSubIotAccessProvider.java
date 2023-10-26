package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.impl.PubSubPipe.GCP_HOST;
import static com.google.bos.udmi.service.messaging.impl.PubSubPipe.getTransportChannelProvider;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.bos.udmi.service.messaging.impl.PubSubPipe;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.IotAccess;

/**
 * An access provider that uses PubSub topics/subscriptions for communication.
 */
public class PubSubIotAccessProvider extends IotAccessBase {

  private final String projectId;
  private final String topic;
  private final Publisher publisher;

  PubSubIotAccessProvider(IotAccess iotAccess) {
    super(iotAccess);
    projectId = getProjectId(iotAccess);
    topic = requireNonNull((String) options.get("topic"), "topic option not supplied");
    publisher = ifNotNullGet(topic, this::getPublisher);
  }

  private Publisher getPublisher(String topicName) {
    try {
      ProjectTopicName projectTopicName = ProjectTopicName.of(projectId, topicName);
      Publisher.Builder builder = Publisher.newBuilder(projectTopicName);
      String emu = PubSubPipe.getEmulatorHost();
      ifNotNullThen(emu, host -> builder.setChannelProvider(getTransportChannelProvider(host)));
      ifNotNullThen(emu, host -> builder.setCredentialsProvider(NoCredentialsProvider.create()));
      info(format("Publisher %s:%s", Optional.ofNullable(emu).orElse(GCP_HOST), projectTopicName));
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException("While creating publisher", e);
    }
  }

  @Override
  protected Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    // Sticky config isn't supported (nor required) for PubSub reflector, so always return empty.
    return new SimpleEntry<>(1L, "{}");
  }

  @Override
  protected Set<String> getRegistriesForRegion(String region) {
    return null;
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected void sendCommandBase(String registryId, String deviceId, SubFolder folder,
      String message) {
    System.err.println("Send command!");
  }

  @Override
  protected String updateConfig(String registryId, String deviceId, String config, Long version) {
    publish(registryId, deviceId, SubType.CONFIG, SubFolder.UPDATE, config);
    return config;
  }

  private void publish(String registryId, String deviceId, SubType subType, SubFolder subFolder, String data) {
    String topicNameString = publisher.getTopicNameString();
    try {
      Envelope envelope = new Envelope();
      envelope.deviceRegistryId = registryId;
      envelope.deviceId = deviceId;
      envelope.subType = subType;
      envelope.subFolder = subFolder;
      Map<String, String> stringMap = toMap(envelope).entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, entry -> (String) entry.getValue()));
      PubsubMessage message = PubsubMessage.newBuilder()
          .putAllAttributes(stringMap)
          .setData(ByteString.copyFromUtf8(data))
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      String publishedId = publish.get();
      debug(format("Published PubSub %s/%s to %s as %s", subType, subFolder,
          topicNameString, publishedId));
    } catch (Exception e) {
      throw new RuntimeException("While publishing message to " + topicNameString, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("fetchDevice not implemented for PubSub");
  }

  @Override
  public String fetchState(String deviceRegistryId, String deviceId) {
    throw new RuntimeException("fetchState not implemented for PubSub");
  }

  @Override
  public CloudModel listDevices(String deviceRegistryId) {
    throw new RuntimeException("listDevices not implemented for PubSub");
  }

  @Override
  public CloudModel modelDevice(String deviceRegistryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modelDevice not implemented for PubSub");
  }

  @Override
  String fetchRegistryMetadata(String registryId, String metadataKey) {
    throw new RuntimeException("fetchRegistryMetadata not implemented for PubSub");
  }
}
