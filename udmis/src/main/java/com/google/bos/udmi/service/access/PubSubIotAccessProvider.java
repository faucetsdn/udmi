package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.impl.PubSubPipe.GCP_HOST;
import static com.google.bos.udmi.service.messaging.impl.PubSubPipe.SOURCE_KEY;
import static com.google.bos.udmi.service.messaging.impl.PubSubPipe.getTransportChannelProvider;
import static com.google.udmi.util.Common.CATEGORY_PROPERTY_KEY;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SOURCE_SEPARATOR;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.bos.udmi.service.messaging.impl.PubSubPipe;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
    boolean enabled = isNotEmpty(projectId);
    if (enabled) {
      topic = requireNonNull(options.get("topic"), "topic option not supplied");
      publisher = getPublisher(topic);
    } else {
      topic = null;
      publisher = null;
    }
  }

  @Override
  public Entry<Long, String> fetchConfig(String registryId, String deviceId) {
    // Sticky config isn't supported (nor required) for PubSub reflector, so always return empty.
    return new SimpleEntry<>(1L, "{}");
  }

  @Override
  public Set<String> getRegistriesForRegion(String region) {
    return ImmutableSet.of();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void sendCommandBase(Envelope envelope, SubFolder folder, String message) {
    publish(envelope, SubType.COMMANDS.value(), folder, message);
  }

  @Override
  public String updateConfig(Envelope envelope, String config, Long version) {
    publish(envelope, SubType.CONFIG.value(), null, config);
    return config;
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

  private void publish(Envelope envelope, String category, SubFolder folder, String data) {
    String topicNameString = publisher.getTopicNameString();
    Map<String, String> stringMap = new HashMap<>();
    try {
      stringMap.put(REGISTRY_ID_PROPERTY_KEY, envelope.deviceRegistryId);
      stringMap.put(DEVICE_ID_KEY, envelope.deviceId);
      stringMap.put(CATEGORY_PROPERTY_KEY, category);

      int index = envelope.source == null ? -1 : envelope.source.indexOf(SOURCE_SEPARATOR);
      String userPart = ifNotNullGet(envelope.source, s -> s.substring(index + 1));
      ifNotNullThen(userPart, () -> stringMap.put(SOURCE_KEY, SOURCE_SEPARATOR + userPart));

      ifNotNullThen(folder, () -> stringMap.put(SUBFOLDER_PROPERTY_KEY, folder.value()));
      PubsubMessage message = PubsubMessage.newBuilder()
          .putAllAttributes(stringMap)
          .setData(ByteString.copyFromUtf8(data))
          .build();
      ApiFuture<String> publish = publisher.publish(message);
      String publishedId = publish.get();
      debug(format("Reflected PubSub %s/%s to %s as %s", category, folder, topicNameString,
          publishedId));
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("While publishing message to " + topicNameString, e);
    }
  }

  @Override
  public CloudModel fetchDevice(String registryId, String deviceId) {
    throw new RuntimeException("fetchDevice not implemented for PubSub");
  }

  @Override
  public String fetchState(String registryId, String deviceId) {
    throw new RuntimeException("fetchState not implemented for PubSub");
  }

  @Override
  public CloudModel listDevices(String registryId, Consumer<Integer> progress) {
    throw new RuntimeException("listDevices not implemented for PubSub");
  }

  @Override
  public CloudModel modelDevice(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modelDevice not implemented for PubSub");
  }

  @Override
  public CloudModel modelRegistry(String registryId, String deviceId, CloudModel cloudModel) {
    throw new RuntimeException("modelDevice not implemented for PubSub");
  }

  @Override
  public String fetchRegistryMetadata(String registryId, String metadataKey) {
    // Metadata is not supported by PubSub, so just pretend there is none.
    return null;
  }
}
