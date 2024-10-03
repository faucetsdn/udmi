package com.google.bos.iot.core.proxy;

import static com.google.bos.iot.core.proxy.ProxyTarget.STATE_TOPIC;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.validator.Validator.TOOLS_FUNCTIONS_VERSION;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.GATEWAY_ID_KEY;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.SOURCE_KEY;
import static com.google.udmi.util.Common.SOURCE_SEPARATOR_REGEX;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.mapCast;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.JsonUtil.toObject;
import static com.google.udmi.util.PubSubReflector.USER_NAME_DEFAULT;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.api.client.util.Base64;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Credential;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiEvents;
import udmi.schema.UdmiState;

/**
 * Publish messages using the iot core reflector.
 */
public class IotReflectorClient implements MessagePublisher {

  public static final String UDMI_REFLECT = "UDMI-REFLECT";
  static final String REFLECTOR_KEY_ALGORITHM = "RS256";
  private static final String SYSTEM_SUBFOLDER = "system";
  private static final String UDMIS_SOURCE = "udmis";
  private static final String EVENTS_TYPE = "events";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";
  private static final String UDMI_TOPIC = "events/" + SubFolder.UDMI;
  private static final long CONFIG_TIMEOUT_SEC = 5;
  private static final int UPDATE_RETRIES = 6;
  private static final Collection<String> COPY_IDS = ImmutableSet.of(DEVICE_ID_KEY, GATEWAY_ID_KEY,
      SUBTYPE_PROPERTY_KEY, SUBFOLDER_PROPERTY_KEY, TRANSACTION_KEY, PUBLISH_TIME_KEY);
  private static final String sessionId = format("%06x", (int) (Math.random() * 0x1000000L));
  private static final String TRANSACTION_ID_PREFIX = "RC:";
  private static final String sessionPrefix = TRANSACTION_ID_PREFIX + sessionId + ".";
  private static final AtomicInteger sessionCounter = new AtomicInteger();
  private final String udmiVersion;
  private final CountDownLatch initialConfigReceived = new CountDownLatch(1);
  private final CountDownLatch initializedStateSent = new CountDownLatch(1);
  private final CountDownLatch validConfigReceived = new CountDownLatch(1);
  private final int requiredVersion;
  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();
  private final MessagePublisher publisher;
  private final String subscriptionId;
  private final String registryId;
  private final String projectId;
  private final String updateTo;
  private final IotProvider iotProvider;
  private final boolean enforceUdmiVersion;
  private final Function<Envelope, Boolean> messageFilter;
  private final String userName;
  private Date reflectorStateTimestamp;
  private boolean isInstallValid;
  private boolean active;
  private Exception syncFailure;
  private SetupUdmiConfig udmiInfo;
  private int retries;

  /**
   * Create a new reflector instance.
   *
   * @param iotConfig       configuration file
   * @param requiredVersion version of the functions that are required by the tools
   */
  public IotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion) {
    this(iotConfig, requiredVersion, null);
  }

  public IotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion,
      Function<Envelope, Boolean> messageFilter) {
    Preconditions.checkState(requiredVersion >= TOOLS_FUNCTIONS_VERSION,
        format("Min required version %s not satisfied by tools version %s", TOOLS_FUNCTIONS_VERSION,
            requiredVersion));
    this.requiredVersion = requiredVersion;
    this.enforceUdmiVersion = isTrue(iotConfig.enforce_version);
    this.messageFilter = ofNullable(messageFilter).orElse(this::userMessageFilter);
    registryId = SiteModel.getRegistryActual(iotConfig);
    projectId = iotConfig.project_id;
    udmiVersion = ofNullable(iotConfig.udmi_version).orElseGet(Common::getUdmiVersion);
    updateTo = iotConfig.update_to;
    String prefix = getNamespacePrefix(iotConfig.udmi_namespace);
    iotProvider = ofNullable(iotConfig.iot_provider).orElse(IotProvider.GBOS);
    userName = ofNullable(iotConfig.user_name).orElse(USER_NAME_DEFAULT);
    iotConfig.iot_provider = iotProvider;
    String clientId = format("//%s/%s/%s %s", iotProvider, projectId, prefix, registryId);
    try {
      System.err.println("Instantiating reflector client " + clientId);
      publisher = MessagePublisher.from(iotConfig, this::messageHandler, this::errorHandler);
    } catch (Exception e) {
      throw new RuntimeException("While creating reflector client " + clientId, e);
    }
    subscriptionId = publisher.getSubscriptionId();
    System.err.println("Subscribed to " + subscriptionId);

    try {
      System.err.println("Starting initial UDMI setup process");
      if (!initialConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
        System.err.println("Ignoring initial config received timeout (config likely empty)");
      }
      retries = updateTo == null ? 1 : UPDATE_RETRIES;
      while (validConfigReceived.getCount() > 0) {
        initializeReflectorState();
        initializedStateSent.countDown();
        if (!validConfigReceived.await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
          retries--;
          if (retries <= 0) {
            throw new RuntimeException(
                "Config sync timeout expired. Investigate UDMI cloud functions install.",
                syncFailure);
          }
        }
      }

      active = true;
    } catch (Exception e) {
      publisher.close();
      throw new RuntimeException("Waiting for initial config", e);
    }
  }

  private boolean userMessageFilter(Envelope envelope) {
    return envelope.source == null || userName.equals(envelope.source);
  }

  /**
   * Make an execution configuration that's used for reflector operations.
   *
   * @param iotConfig  basic non-reflector configuration
   * @param registryId the registry that will be reflected
   */
  public static ExecutionConfiguration makeReflectConfiguration(ExecutionConfiguration iotConfig,
      String registryId) {
    ExecutionConfiguration reflectConfiguration = new ExecutionConfiguration();
    reflectConfiguration.iot_provider = iotConfig.iot_provider;
    reflectConfiguration.project_id = iotConfig.project_id;
    reflectConfiguration.bridge_host = iotConfig.bridge_host;
    reflectConfiguration.reflector_endpoint = iotConfig.reflector_endpoint;
    reflectConfiguration.cloud_region = ofNullable(iotConfig.reflect_region)
        .orElse(iotConfig.cloud_region);

    reflectConfiguration.site_model = iotConfig.site_model;
    reflectConfiguration.registry_id = UDMI_REFLECT;
    reflectConfiguration.udmi_namespace = iotConfig.udmi_namespace;
    // Intentionally map registry -> device because of reflection registry semantics.
    reflectConfiguration.device_id = registryId;

    return reflectConfiguration;
  }

  /**
   * Get a new unique (not the same as previous one) transaction id.
   *
   * @return new unique transaction id
   */
  public static synchronized String getNextTransactionId() {
    return format("%s%04d", sessionPrefix, sessionCounter.incrementAndGet());
  }

  private void initializeReflectorState() {
    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = System.getenv("USER");
    udmiState.setup.transaction_id = getNextTransactionId();
    udmiState.setup.update_to = updateTo;
    try {
      reflectorStateTimestamp = new Date();
      System.err.printf("Setting state version %s timestamp %s%n",
          udmiVersion, isoConvert(reflectorStateTimestamp));
      setReflectorState(udmiState);
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
  }

  private void setReflectorState(UdmiState udmiState) {
    Map<String, Object> map = new HashMap<>();
    map.put(TIMESTAMP_KEY, reflectorStateTimestamp);
    map.put(VERSION_KEY, udmiVersion);
    map.put(SubFolder.UDMI.value(), udmiState);

    System.err.println("UDMI setting reflectorState: " + stringify(map));

    publisher.publish(registryId, getReflectorTopic(), stringify(map));
  }

  private String getReflectorTopic() {
    return switch (iotProvider) {
      case MQTT -> SubType.REFLECT.toString();
      default -> STATE_TOPIC;
    };
  }

  private String getPublishTopic() {
    return switch (iotProvider) {
      case MQTT -> SubType.REFLECT.toString();
      default -> UDMI_TOPIC;
    };
  }

  @Override
  public Credential getCredential() {
    return publisher.getCredential();
  }

  private void messageHandler(String topic, String payload) {
    if (payload.length() == 0) {
      return;
    }
    byte[] rawData = payload.getBytes();
    boolean base64 = rawData[0] != '{';

    if ("null".equals(payload)) {
      return;
    }
    Map<String, Object> messageMap = asMap(
        new String(base64 ? Base64.decodeBase64(rawData) : rawData));
    try {
      Envelope envelope = parseMessageTopic(topic);
      ifNotNullThen(envelope.source, source -> messageMap.put(SOURCE_KEY, source),
          () -> messageMap.remove(SOURCE_KEY));
      if (SubType.CONFIG == envelope.subType) {
        ensureCloudSync(messageMap);
      } else if (SubType.COMMANDS == envelope.subType) {
        handleEncapsulatedMessage(envelope, messageMap);
      } else {
        throw new RuntimeException("Unknown message category " + envelope.subType);
      }
    } catch (Exception e) {
      if (isInstallValid) {
        handleReceivedMessage(extractAttributes(messageMap),
            new ErrorContainer(e, payload, getTimestamp()));
      } else {
        throw e;
      }
    }
  }

  private void handleEncapsulatedMessage(Envelope envelope, Map<String, Object> messageMap) {
    if (!isInstallValid || !messageFilter.apply(envelope)) {
      return;
    }
    Map<String, String> attributes = extractAttributes(messageMap);
    String payload = (String) messageMap.remove("payload");
    handleReceivedMessage(attributes, toObject(decodeBase64(payload)));
  }

  @NotNull
  private Map<String, String> extractAttributes(Map<String, Object> messageMap) {
    Map<String, String> attributes = new TreeMap<>();
    attributes.put("projectId", projectId);
    attributes.put("deviceRegistryId", registryId);
    COPY_IDS.forEach(key -> attributes.put(key, (String) messageMap.get(key)));
    attributes.put("deviceNumId", MOCK_DEVICE_NUM_ID);
    return attributes;
  }

  private void handleReceivedMessage(Map<String, String> attributes, Object message) {
    Validator.MessageBundle messageBundle = new Validator.MessageBundle();
    messageBundle.attributes = attributes;
    if (message instanceof String stringMessage) {
      messageBundle.rawMessage = stringMessage;
    } else {
      messageBundle.message = mapCast(message);
    }
    if (SubFolder.UDMI.value().equals(attributes.get(SUBFOLDER_PROPERTY_KEY))
        && processUdmiMessage(messageBundle)) {
      return;
    }

    messages.offer(messageBundle);
  }

  private boolean processUdmiMessage(Validator.MessageBundle messageBundle) {
    String subType = messageBundle.attributes.get(SUBTYPE_PROPERTY_KEY);
    if (shouldIgnoreMessage(messageBundle)) {
      return true;
    } else if (SubType.EVENTS.value().equals(subType)) {
      processUdmiEvent(messageBundle.message);
      return true;
    } else if (SubType.CONFIG.value().equals(subType)) {
      ensureCloudSync(messageBundle.message);
    } else {
      throw new RuntimeException("Unexpected receive type " + subType);
    }
    return false;
  }

  private boolean shouldIgnoreMessage(Validator.MessageBundle messageBundle) {
    String transactionId = messageBundle.attributes.get(TRANSACTION_KEY);
    return transactionId != null && !transactionId.startsWith(sessionPrefix);
  }

  private void processUdmiEvent(Map<String, Object> message) {
    UdmiEvents events = convertTo(UdmiEvents.class, message);
    events.logentries.forEach(
        entry -> System.err.printf("%s %s%n", isoConvert(entry.timestamp), entry.message));
  }

  private boolean ensureCloudSync(Map<String, Object> message) {
    try {
      initialConfigReceived.countDown();
      if (initializedStateSent.getCount() > 0) {
        return false;
      }

      // Check for LEGACY UDMIS folder, and use that instead for backwards compatibility. Once
      // UDMI version 1.4.2+ is firmly established, this can be simplified to just UDMI.
      boolean legacyConfig = message.containsKey("udmis");
      final UdmiConfig reflectorConfig;
      if (legacyConfig) {
        System.err.println("UDMI using LEGACY config format, function install upgrade required");
        reflectorConfig = new UdmiConfig();
        Map<String, Object> udmisMessage = toMap(message.get("udmis"));
        SetupUdmiConfig udmis = ofNullable(
            convertTo(SetupUdmiConfig.class, udmisMessage))
            .orElseGet(SetupUdmiConfig::new);
        reflectorConfig.last_state = getDate((String) udmisMessage.get("last_state"));
        reflectorConfig.setup = udmis;
      } else {
        reflectorConfig = convertTo(UdmiConfig.class,
            ofNullable(message.get(SubFolder.UDMI.value())).orElse(message));
      }
      System.err.println("UDMI received reflectorConfig: " + stringify(reflectorConfig));
      Date lastState = reflectorConfig.last_state;
      System.err.printf("UDMI matching last_state %s against expected %s%n",
          isoConvert(lastState), isoConvert(reflectorStateTimestamp));

      boolean timestampMatch = dateEquals(lastState, reflectorStateTimestamp);
      boolean versionMatch = ifNotNullGet(updateTo, to -> to.equals(reflectorConfig.setup.udmi_ref),
          true);

      if (timestampMatch && versionMatch) {
        udmiInfo = reflectorConfig.setup;
        if (!udmiVersion.equals(udmiInfo.udmi_version)) {
          System.err.println("UDMI version mismatch: " + udmiVersion);
          checkState(!enforceUdmiVersion, "Strict UDMI version matching enabled");
        }

        System.err.printf("UDMI functions support versions %s:%s (required %s)%n",
            udmiInfo.functions_min, udmiInfo.functions_max, requiredVersion);
        String baseError = format("UDMI required functions version %d not allowed",
            requiredVersion);
        if (requiredVersion < udmiInfo.functions_min) {
          throw new RuntimeException(
              format("%s: min supported %s. Please update local UDMI install.", baseError,
                  udmiInfo.functions_min));
        }
        if (requiredVersion > udmiInfo.functions_max) {
          throw new RuntimeException(
              format("%s: max supported %s. Please update cloud UDMIS install.",
                  baseError, udmiInfo.functions_max));
        }
        isInstallValid = true;
        validConfigReceived.countDown();
      } else if (!versionMatch) {
        System.err.println("UDMI update version mismatch... waiting for retry...");
      } else {
        System.err.println("UDMI ignoring mismatching timestamp " + isoConvert(lastState));
      }
    } catch (Exception e) {
      syncFailure = e;
    }

    // Even through setup might be valid, return false to not process this config message.
    return false;
  }

  private Envelope parseMessageTopic(String topic) {
    List<String> parts = new ArrayList<>(Arrays.asList(topic.substring(1).split("/")));
    String leader = parts.remove(0);
    if ("devices".equals(leader)) {
      // Next field is registry, not device, since the reflector device id is the site registry.
      String deviceId = parts.remove(0);
      checkState(registryId.equals(deviceId),
          format("device id %s does not match expected %s", deviceId, registryId));
    } else if ("r".equals(leader)) {
      // Next field is registry, not device, since the reflector device id is the site registry.
      String parsedReg = parts.remove(0);
      checkState(UDMI_REFLECT.equals(parsedReg),
          format("registry id %s does not match expected %s", parsedReg, UDMI_REFLECT));
      String devSep = parts.remove(0);
      checkState("d".equals(devSep), format("unexpected dev separator %s", devSep));
      String deviceId = parts.remove(0);
      checkState(registryId.equals(deviceId),
          format("registry id %s does not match expected %s", deviceId, registryId));
    } else {
      throw new RuntimeException("Unknown topic string " + topic);
    }

    System.err.printf("Envelope conversion from %s%n", topic);
    Envelope envelope = new Envelope();
    String[] bits1 = parts.remove(0).split(SOURCE_SEPARATOR_REGEX);
    checkState(parts.isEmpty() || bits1.length == 1, "Malformed topic: " + topic);
    envelope.subType = SubType.fromValue(bits1[0]);
    if (parts.isEmpty()) {
      envelope.source = bits1.length > 1 ? bits1[1] : null;
    } else {
      String[] bits2 = parts.remove(0).split(SOURCE_SEPARATOR_REGEX);
      envelope.subFolder = SubFolder.fromValue(bits2[0]);
      envelope.source = bits2.length > 1 ? bits2[1] : null;
    }
    checkState(parts.isEmpty());
    System.err.printf("Envelope conversion from %s as %s%n", topic, stringifyTerse(envelope));
    return envelope;
  }

  private void errorHandler(Throwable throwable) {
    System.err.printf("Received mqtt client error: %s at %s%n",
        throwable.getMessage(), getTimestamp());
    close();
  }

  @Override
  public String getSubscriptionId() {
    return subscriptionId;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public Validator.MessageBundle takeNextMessage(QuerySpeed timeout) {
    try {
      if (!active) {
        throw new IllegalStateException("Reflector client not active");
      }
      return messages.poll(timeout.seconds(), TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("While taking next message", e);
    }
  }

  @Override
  public String publish(String deviceId, String topic, String data) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;
    envelope.deviceId = deviceId;
    String[] parts = topic.split("/", 2);
    envelope.subFolder = SubFolder.fromValue(parts[0]);
    envelope.subType = SubType.fromValue(parts[1]);
    envelope.payload = GeneralUtils.encodeBase64(data);
    String transactionId = getNextTransactionId();
    envelope.transactionId = transactionId;
    envelope.publishTime = new Date();
    publisher.publish(registryId, getPublishTopic(), stringify(envelope));
    return transactionId;
  }

  @Override
  public void close() {
    active = false;
    if (publisher != null) {
      publisher.close();
    }
  }

  @Override
  public SetupUdmiConfig getVersionInformation() {
    return requireNonNull(udmiInfo, "udmi version information not available");
  }

  public String getBridgeHost() {
    return publisher.getBridgeHost();
  }

  static class MessageBundle {

    Map<String, Object> message;
    Map<String, String> attributes;
  }

}
