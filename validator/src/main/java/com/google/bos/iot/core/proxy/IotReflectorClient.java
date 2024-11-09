package com.google.bos.iot.core.proxy;

import static com.google.bos.iot.core.proxy.ProxyTarget.STATE_TOPIC;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.PublishPriority.HIGH;
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
import static com.google.udmi.util.Common.UNKNOWN_UDMI_VERSION;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.mapCast;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.toObject;
import static com.google.udmi.util.PubSubReflector.USER_NAME_DEFAULT;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import com.google.api.client.util.Base64;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.ImpulseRunningAverage;
import com.google.daq.mqtt.validator.Validator;
import com.google.daq.mqtt.validator.Validator.ErrorContainer;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.SiteModel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
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
  public static final String REFLECTOR_KEY_ALGORITHM = "RS256";
  private static final Date SYSTEM_START_TIMESTAMP = new Date();
  private static final String SYSTEM_SUBFOLDER = "system";
  private static final String UDMIS_SOURCE = "udmis";
  private static final String EVENTS_TYPE = "events";
  private static final String MOCK_DEVICE_NUM_ID = "123456789101112";
  private static final String UDMI_TOPIC = "events/" + SubFolder.UDMI;
  private static final long CONFIG_TIMEOUT_SEC = 5;
  private static final int UPDATE_RETRIES = 6;
  private static final Collection<String> COPY_IDS = ImmutableSet.of(DEVICE_ID_KEY, GATEWAY_ID_KEY,
      SUBTYPE_PROPERTY_KEY, SUBFOLDER_PROPERTY_KEY, TRANSACTION_KEY, PUBLISH_TIME_KEY);
  public static final String TRANSACTION_ID_PREFIX = "RC:";
  private static final String sessionId = format("%06x", (int) (Math.random() * 0x1000000L));
  private static final String sessionPrefix = TRANSACTION_ID_PREFIX + sessionId + ".";
  private static final AtomicInteger sessionCounter = new AtomicInteger();
  private static final long RESYNC_INTERVAL_SEC = 30;
  private static final Map<MessagePublisher, CountDownLatch> pubLatches = new ConcurrentHashMap<>();
  private static final Map<MessagePublisher, AtomicInteger> pubCounts = new ConcurrentHashMap<>();
  private final String udmiVersion;
  private final CountDownLatch initialConfigReceived = new CountDownLatch(1);
  private final CountDownLatch initializedStateSent = new CountDownLatch(1);
  private final ScheduledExecutorService tickExecutor = newSingleThreadScheduledExecutor();
  private final int requiredVersion;
  private final BlockingQueue<Validator.MessageBundle> messages = new LinkedBlockingQueue<>();
  private final MessagePublisher publisher;
  private final String subscriptionId;
  private final String registryId;
  private final String projectId;
  private final String updateVersion;
  private final IotProvider iotProvider;
  private final boolean enforceUdmiVersion;
  private final Function<Envelope, Boolean> messageFilter;
  private final String userName;
  private final String toolName;
  private final ExecutionConfiguration executionConfig;
  private boolean isInstallValid;
  private boolean active;
  private Exception syncFailure;
  private SetupUdmiConfig udmiInfo;
  private int retries;
  private String expectedTxnId;
  private Instant txnStartTime;
  private final ImpulseRunningAverage publishStats = new ImpulseRunningAverage("Message publish");
  private final ImpulseRunningAverage receiveStats = new ImpulseRunningAverage("Message receive");
  private final Set<ImpulseRunningAverage> samplers = ImmutableSet.of(publishStats, receiveStats);

  /**
   * Create a new reflector instance.
   *
   * @param iotConfig       configuration file
   * @param requiredVersion version of the functions that are required by the tools
   * @param toolName        tool name using this reflector
   */
  public IotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion,
      String toolName) {
    this(iotConfig, requiredVersion, toolName, null);
  }

  /**
   * Basic client that accepts a custom message filter.
   */
  public IotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion,
      String toolName, Function<Envelope, Boolean> messageFilter) {
    Preconditions.checkState(requiredVersion >= TOOLS_FUNCTIONS_VERSION,
        format("Min required version %s not satisfied by tools version %s", TOOLS_FUNCTIONS_VERSION,
            requiredVersion));
    this.executionConfig = iotConfig;
    this.requiredVersion = requiredVersion;
    this.enforceUdmiVersion = isTrue(iotConfig.enforce_version);
    this.messageFilter = ofNullable(messageFilter).orElse(this::userMessageFilter);
    registryId = SiteModel.getRegistryActual(iotConfig);
    projectId = iotConfig.project_id;
    udmiVersion = ofNullable(iotConfig.udmi_version).orElseGet(Common::getUdmiVersion);
    updateVersion = iotConfig.update_to;
    iotProvider = ofNullable(iotConfig.iot_provider).orElse(IotProvider.GBOS);
    userName = ofNullable(iotConfig.user_name).orElse(USER_NAME_DEFAULT);
    this.toolName = toolName;
    iotConfig.iot_provider = iotProvider;
    String prefix = getNamespacePrefix(iotConfig.udmi_namespace);
    String clientId = format("//%s/%s/%s %s", iotProvider, projectId, prefix, registryId);
    try {
      System.err.println("Instantiating reflector client " + clientId);
      publisher = MessagePublisher.from(iotConfig, this::messageHandler, this::errorHandler);
      pubCounts.computeIfAbsent(publisher, key -> new AtomicInteger());
      pubLatches.computeIfAbsent(publisher, key -> new CountDownLatch(1));
    } catch (Exception e) {
      throw new RuntimeException("While creating reflector client " + clientId, e);
    }
    subscriptionId = publisher.getSubscriptionId();
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
  public static String getNextTransactionId() {
    return format("%s%08x", sessionPrefix, sessionCounter.incrementAndGet());
  }

  private void timerTick() {
    samplers.forEach(value -> info(value.getMessage()));
    setReflectorState();
  }

  private synchronized void setReflectorState() {
    if (isInstallValid && expectedTxnId != null) {
      error(format("Missing UDMI reflector state reply for %s after %ss", expectedTxnId,
          Duration.between(txnStartTime, getNowInstant()).toSeconds()));
      errorHandler(
          new IllegalStateException("Aborting due to missing transaction reply " + expectedTxnId));
      return;
    }
    expectedTxnId = getNextTransactionId();
    txnStartTime = getNowInstant();

    UdmiState udmiState = new UdmiState();
    udmiState.setup = new SetupUdmiState();
    udmiState.setup.user = System.getenv("USER");
    udmiState.setup.transaction_id = expectedTxnId;
    udmiState.setup.update_to = updateVersion;
    udmiState.setup.msg_source = userName;
    udmiState.setup.tool_name = toolName;
    udmiState.setup.udmi_version = executionConfig.udmi_version;
    udmiState.setup.udmi_commit = executionConfig.udmi_commit;
    udmiState.setup.udmi_ref = executionConfig.udmi_ref;
    udmiState.setup.udmi_timever = executionConfig.udmi_timever;
    try {
      debug(format("Setting state version %s timestamp %s%n",
          udmiVersion, isoConvert(SYSTEM_START_TIMESTAMP)));
      Map<String, Object> map = new HashMap<>();
      map.put(TIMESTAMP_KEY, SYSTEM_START_TIMESTAMP);
      map.put(VERSION_KEY, udmiVersion);
      map.put(SubFolder.UDMI.value(), udmiState);

      if (isInstallValid) {
        debug("Sending UDMI reflector state: " + stringifyTerse(udmiState.setup));
      } else {
        info("Sending UDMI reflector state: " + stringify(map));
      }

      publishStats.update();
      publisher.publish(registryId, getReflectorTopic(), stringifyTerse(map), HIGH);
    } catch (Exception e) {
      throw new RuntimeException("Could not set reflector state", e);
    }
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
    receiveStats.update();
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
      if (envelope == null) {
        return;
      }
      ifNotNullThen(envelope.source, source -> messageMap.put(SOURCE_KEY, source),
          () -> messageMap.remove(SOURCE_KEY));
      if (SubType.CONFIG == envelope.subType) {
        ensureCloudSync(messageMap);
      } else if (SubType.COMMANDS == envelope.subType) {
        handleEncapsulatedMessage(envelope, messageMap);
      } else {
        throw new RuntimeException("Unknown message category " + envelope.subType);
      }
    } catch (IllegalStateException e) {
      throw e;
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

  private synchronized void ensureCloudSync(Map<String, Object> message) {
    try {
      initialConfigReceived.countDown();
      if (initializedStateSent.getCount() > 0) {
        return;
      }

      UdmiConfig reflectorConfig = convertTo(UdmiConfig.class,
          ofNullable(message.get(SubFolder.UDMI.value())).orElse(message));

      boolean shouldConsiderReply = reflectorConfig.reply.msg_source.equals(userName);
      String transactionId = reflectorConfig.reply.transaction_id;
      boolean matchingTxnId = transactionId.equals(expectedTxnId);
      boolean matchingSession = transactionId.startsWith(sessionPrefix);

      if (!isInstallValid) {
        info("Received UDMI reflector initial config: " + stringify(reflectorConfig));
      }

      if (!shouldConsiderReply) {
        return;
      } else if (!matchingSession) {
        info(
            format("Received UDMI reflector other session %s != %s", transactionId, sessionPrefix));
        throw new IllegalStateException("There can (should) be only one instance on a channel");
      } else if (!matchingTxnId) {
        debug(format("Ignoring unexpected reply from this session %s != %s", transactionId,
            expectedTxnId));
        return;
      }

      debug("Received UDMI reflector matching config reply " + expectedTxnId);

      Date lastState = reflectorConfig.last_state;
      boolean timestampMatch = dateEquals(lastState, SYSTEM_START_TIMESTAMP);
      boolean updateMatch = ifNotNullGet(updateVersion,
          to -> to.equals(reflectorConfig.setup.udmi_ref),
          true);
      boolean userSourceMatch = userName.equals(reflectorConfig.reply.msg_source);
      if (timestampMatch && updateMatch && userSourceMatch) {
        udmiInfo = reflectorConfig.setup;
        if (!udmiVersion.equals(udmiInfo.udmi_version)) {
          if (enforceUdmiVersion || !(udmiVersion.equals(UNKNOWN_UDMI_VERSION) || isInstallValid)) {
            System.err.printf("UDMI version mismatch: %s does not match %s%n",
                udmiVersion, udmiInfo.udmi_version);
          }
          checkState(!enforceUdmiVersion, "Strict UDMI version matching enabled");
        }

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
        expectedTxnId = null;
        debug(format("UDMI reflector state transaction took %ss",
            Duration.between(txnStartTime, getNowInstant()).toSeconds()));
        pubLatches.get(publisher).countDown();
      } else if (!updateMatch) {
        System.err.println("UDMI update version mismatch... waiting for retry...");
      } else if (!timestampMatch) {
        System.err.printf("UDMI last_state %s does not match expected %s%n",
            isoConvert(lastState), isoConvert(SYSTEM_START_TIMESTAMP));
      } else if (!userSourceMatch) {
        System.err.println("UDMI msg_source does not match local user, ignoring.");
      } else {
        throw new RuntimeException("Unexpected if condition");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      syncFailure = e;
    }
  }

  private void debug(String message) {
    // TODO: Implement some kind of actual log-level control.
  }

  private void info(String message) {
    // TODO: Implement some kind of actual log-level control.
    System.err.println(message);
  }

  private void error(String message) {
    // TODO: Implement some kind of actual log-level control.
    System.err.println(message);
  }

  private Envelope parseMessageTopic(String topic) {
    List<String> parts = new ArrayList<>(Arrays.asList(topic.substring(1).split("/")));
    String leader = parts.remove(0);
    if ("devices".equals(leader)) {
      // Next field is registry, not device, since the reflector device id is the site registry.
      String deviceId = parts.remove(0);
      if (!registryId.equals(deviceId)) {
        return null;
      }
    } else if ("r".equals(leader)) {
      // Next field is registry, not device, since the reflector device id is the site registry.
      String parsedReg = parts.remove(0);
      checkState(UDMI_REFLECT.equals(parsedReg),
          format("registry id %s does not match expected %s", parsedReg, UDMI_REFLECT));
      String devSep = parts.remove(0);
      checkState("d".equals(devSep), format("unexpected dev separator %s", devSep));
      String deviceId = parts.remove(0);
      if (!registryId.equals(deviceId)) {
        return null;
      }
    } else {
      throw new RuntimeException("Unknown topic string " + topic);
    }

    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = registryId;

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

    return envelope;
  }

  protected void errorHandler(Throwable throwable) {
    receiveStats.update();
    System.err.printf("Received mqtt client error: %s at %s%n",
        throwable.getMessage(), getTimestamp());
    close();
  }

  @Override
  public String getSubscriptionId() {
    return subscriptionId;
  }

  @Override
  public void activate() {

    try {
      // Some publishers are shared, while others are unique, so handle accordingly.
      if (pubCounts.get(publisher).getAndIncrement() > 0) {
        ifTrueThen(pubLatches.get(publisher).getCount() > 0,
            () -> System.err.println("Waiting for the other shoe to drop..."));
        pubLatches.get(publisher).await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS);
        active = true;
        isInstallValid = true;
        return;
      }

      publisher.activate();

      System.err.println("Starting initial UDMI setup process");
      retries = updateVersion == null ? 1 : UPDATE_RETRIES;
      while (pubLatches.get(publisher).getCount() > 0) {
        setReflectorState();
        initializedStateSent.countDown();
        if (!pubLatches.get(publisher).await(CONFIG_TIMEOUT_SEC, TimeUnit.SECONDS)) {
          retries--;
          if (retries <= 0) {
            throw new RuntimeException(
                "Config sync timeout expired. Investigate UDMI cloud functions install.",
                syncFailure);
          }
        }
      }

      tickExecutor.scheduleAtFixedRate(this::timerTick, RESYNC_INTERVAL_SEC,
          RESYNC_INTERVAL_SEC, TimeUnit.SECONDS);

      System.err.println("Subscribed to " + subscriptionId);

      active = true;
    } catch (Exception e) {
      close();
      throw new RuntimeException("Waiting for initial config", e);
    }
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
    publishStats.update();
    publisher.publish(registryId, getPublishTopic(), stringify(envelope));
    return transactionId;
  }

  @Override
  public void close() {
    active = false;
    tickExecutor.shutdown();
    ifTrueThen(pubCounts.get(publisher).decrementAndGet() == 0, publisher::close);
  }

  @Override
  public SetupUdmiConfig getVersionInformation() {
    return requireNonNull(udmiInfo, "udmi version information not available");
  }

  public String getBridgeHost() {
    return publisher.getBridgeHost();
  }

  public String getSessionPrefix() {
    return sessionPrefix;
  }

  static class MessageBundle {

    Map<String, Object> message;
    Map<String, String> attributes;
  }

}
