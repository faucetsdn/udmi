package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.pod.UdmiServicePod.UDMI_VERSION;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.fromString;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.NullMessage;
import com.google.bos.udmi.service.messaging.MessageDispatcher.SentinelPayload;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageBase.BundleException;
import com.google.bos.udmi.service.messaging.impl.PubSubPipe;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.udmi.util.CleanDateFormat;
import com.google.udmi.util.Common;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import udmi.schema.Category;
import udmi.schema.CloudModel;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Entry;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.SystemModel;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;
import udmi.schema.UufiControl;
import udmi.schema.UufiControlBlock;

/**
 * Processor for the Unified UDMI Functional Interface (UUFI).
 */
@ComponentName("uufi")
public class UufiProcessor extends ProcessorBase {

  private static final String UUFI_GATEWAY_ID = "uufi";
  private static final String UUFI_EVENTS_CONTAINER_ID = "uufi_events";
  private static final String UDMIS_SOURCE = "udmis";
  private static final String PUBSUB_UDMIS_SOURCE =
      PubSubPipe.PUBSUB_SOURCE + Common.SOURCE_SEPARATOR + UDMIS_SOURCE;
  private static final String CONTROL_KEY = "control";
  private static final String MISSING_SENTINEL = "missing";

  private boolean pubSubProtocol;

  /**
   * Create a new UUFI processor instance.
   */
  public UufiProcessor(EndpointConfiguration config) {
    super(config);
    pubSubProtocol = Protocol.PUBSUB.equals(config.protocol);
    info("UUFI Processor initialized (pubsub=%s)", pubSubProtocol);
  }

  @VisibleForTesting
  void setPubSubProtocolForTest(boolean pubSubProtocol) {
    this.pubSubProtocol = pubSubProtocol;
  }

  private static boolean isSystemSource(Object source) {
    return UDMIS_SOURCE.equals(source) || PUBSUB_UDMIS_SOURCE.equals(source);
  }

  private static boolean isExcludedErrorChannel(Envelope envelope) {
    return envelope != null
        && (envelope.subFolder == SubFolder.ERROR || envelope.subType == SubType.ERRORS);
  }

  private static boolean hasControlRoot(Object payload) {
    return payload instanceof Map<?, ?> payloadMap && payloadMap.containsKey(CONTROL_KEY);
  }

  private static String extractString(Object primary, String fallback) {
    if (primary instanceof String str && !str.isEmpty()) {
      return str;
    }
    return fallback;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> scrubCloudModelMap(Map<String, Object> raw) {
    Map<String, Object> scrubbed = new HashMap<>(raw);
    scrubbed.remove("password");
    scrubbed.remove("credentials");
    Object deviceIdsObj = scrubbed.get("device_ids");
    if (deviceIdsObj instanceof Map<?, ?> deviceIdsMap) {
      Map<String, Object> scrubbedDevices = new HashMap<>();
      for (Map.Entry<?, ?> entry : deviceIdsMap.entrySet()) {
        Object val = entry.getValue();
        if (val instanceof Map<?, ?> childMap) {
          scrubbedDevices.put(String.valueOf(entry.getKey()),
              scrubCloudModelMap((Map<String, Object>) childMap));
        } else if (val != null) {
          scrubbedDevices.put(String.valueOf(entry.getKey()),
              scrubCloudModelMap(toMap(val)));
        }
      }
      scrubbed.put("device_ids", scrubbedDevices);
    }
    return scrubbed;
  }

  private static String errorCategoryFor(Envelope envelope) {
    if (envelope != null && envelope.subType == SubType.CONFIG) {
      return Category.SYSTEM_CONFIG_PARSE;
    }
    return Category.VALIDATION_DEVICE_RECEIVE;
  }

  @Override
  protected void exceptionHandler(Exception e) {
    if (e instanceof BundleException bundleException) {
      handleUufiBundleException(bundleException);
      return;
    }
    error("Unhandled UUFI processor exception (suppressed from reflect error channel): "
        + friendlyStackTrace(e));
  }

  private void handleUufiBundleException(BundleException bundleException) {
    Map<String, String> attrs =
        bundleException.bundle != null ? bundleException.bundle.attributesMap : null;
    if (attrs == null) {
      warn("Dropping UUFI BundleException without attributes: %s",
          Common.getExceptionMessage(bundleException));
      return;
    }
    final String principal = attrs.get("principal");
    final String transactionId = attrs.get("transactionId");
    Envelope reqEnv = new Envelope();
    reqEnv.subType = catchToNull(() -> SubType.fromValue(attrs.get("subType")));
    reqEnv.subFolder = catchToNull(() -> SubFolder.fromValue(attrs.get("subFolder")));
    reqEnv.deviceRegistryId = attrs.get("deviceRegistryId");
    reqEnv.deviceId = attrs.get("deviceId");
    reqEnv.projectId = attrs.get("projectId");
    reqEnv.principal = principal;
    reqEnv.transactionId = transactionId;

    boolean hasMinimalProps = !Strings.isNullOrEmpty(principal)
        && (pubSubProtocol || (reqEnv.subType != null && reqEnv.subFolder != null));
    if (!hasMinimalProps) {
      warn("Dropping UUFI BundleException without minimal routing properties "
          + "(pubsub=%s, principal=%s, subType=%s, subFolder=%s): %s",
          pubSubProtocol, principal, reqEnv.subType, reqEnv.subFolder,
          Common.getExceptionMessage(bundleException));
      return;
    }
    sendControlError(reqEnv, principal, transactionId, errorCategoryFor(reqEnv), bundleException);
  }

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope envelope = continuation.getEnvelope();

    boolean isUufi = UUFI_GATEWAY_ID.equals(envelope.gatewayId)
        || (pubSubProtocol && UUFI_GATEWAY_ID.equals(containerId));

    try {
      if (isUufi) {
        if (pubSubProtocol) {
          handlePubSubIngress(envelope, message);
        } else {
          handleMqttIngress(envelope, message);
        }
      } else {
        if (isExcludedErrorChannel(envelope)) {
          debug("Dropping excluded error channel %s/%s from UUFI outbound",
              envelope.subType, envelope.subFolder);
          return;
        }
        info("Processing UUFI outbound message %s/%s", envelope.subType, envelope.subFolder);
        handleUufiOutbound(envelope, message);
      }
    } catch (Exception e) {
      error("Error processing UUFI message: " + friendlyStackTrace(e));
    }
  }

  private void handlePubSubIngress(Envelope envelope, Object message) {
    info("Received UUFI PubSub message: type=%s, folder=%s, source=%s, principal=%s",
        envelope.subType, envelope.subFolder, envelope.source, envelope.principal);

    if (isSystemSource(envelope.source)) {
      debug("Ignoring loopback of PubSub message from source %s", envelope.source);
      return;
    }

    if (isHandshake(envelope)) {
      if (message instanceof NullMessage || message instanceof SentinelPayload) {
        warn("Invalid non-object UUFI handshake payload for principal=%s, txn=%s",
            envelope.principal, envelope.transactionId);
        sendControlError(envelope, envelope.principal, envelope.transactionId,
            errorCategoryFor(envelope),
            new IllegalArgumentException("UUFI handshake requires a UdmiState JSON object"));
        return;
      }
      info("Processing UUFI handshake request");
      handleHandshake(envelope, convertTo(UdmiState.class, message));
      return;
    }

    if (envelope.subType == SubType.CONFIG && envelope.subFolder == SubFolder.UDMI) {
      info("Ignoring loopback of UUFI handshake reply");
      return;
    }

    if (isExcludedErrorChannel(envelope)) {
      debug("Dropping excluded error channel %s/%s on UUFI PubSub ingress",
          envelope.subType, envelope.subFolder);
      return;
    }

    String reqPrincipal = envelope.principal;
    String reqTxnId = envelope.transactionId;
    if (Strings.isNullOrEmpty(reqPrincipal)) {
      warn("Dropping UUFI PubSub client message without principal for %s/%s",
          envelope.subType, envelope.subFolder);
      return;
    }

    Object payload = (message instanceof NullMessage) ? null
        : (message instanceof SentinelPayload) ? message : toMap(message);

    if (hasControlRoot(payload)) {
      warn("Dropping client-originated UUFI control message for %s/%s (principal=%s, txn=%s)",
          envelope.subType, envelope.subFolder, reqPrincipal, reqTxnId);
      return;
    }

    try {
      if (envelope.subType == null || envelope.subFolder == null) {
        throw new IllegalArgumentException(String.format(
            "Missing required UUFI envelope fields: subType=%s, subFolder=%s",
            envelope.subType, envelope.subFolder));
      }
      Envelope innerEnvelope = deepCopy(envelope);
      innerEnvelope.payload = null;
      innerEnvelope.gatewayId = null;
      dispatchUufiRequest(innerEnvelope, payload, reqPrincipal, reqTxnId);
    } catch (Exception e) {
      error("Error processing UUFI PubSub request: " + friendlyStackTrace(e));
      sendControlError(envelope, reqPrincipal, reqTxnId, errorCategoryFor(envelope), e);
    }
  }

  private void handleMqttIngress(Envelope envelope, Object message) {
    if (message instanceof NullMessage || message instanceof SentinelPayload) {
      warn("Dropping unwrapped UUFI MQTT message without envelope headers for %s/%s",
          envelope.subType, envelope.subFolder);
      return;
    }

    Map<String, Object> messageMap = toMap(message);
    String principal = extractString(messageMap.get("principal"), envelope.principal);

    info("Received UUFI message: type=%s, folder=%s, gateway=%s, source=%s, principal=%s",
        envelope.subType, envelope.subFolder, envelope.gatewayId, envelope.source, principal);

    if (isSystemSource(envelope.source) || isSystemSource(messageMap.get("source"))) {
      debug("Ignoring loopback of message from udmis source");
      return;
    }

    if (isHandshake(envelope)) {
      info("Processing UUFI handshake request");
      handleHandshake(envelope, extractHandshakeState(envelope, message, messageMap));
      return;
    }

    if (envelope.subType == SubType.CONFIG && envelope.subFolder == SubFolder.UDMI) {
      info("Ignoring loopback of UUFI handshake reply");
      return;
    }

    if (isExcludedErrorChannel(envelope)) {
      debug("Dropping excluded error channel %s/%s on UUFI MQTT ingress",
          envelope.subType, envelope.subFolder);
      return;
    }

    debug("UUFI message keys: " + messageMap.keySet());
    if (messageMap.containsKey("gatewayId")) {
      String gatewayId = (String) messageMap.get("gatewayId");
      debug("UUFI gatewayId: " + gatewayId + ", clientId: " + DistributorPipe.clientId);
      if (gatewayId != null && gatewayId.startsWith(DistributorPipe.clientId)) {
        info("Ignoring loopback of system message from gateway " + gatewayId);
        return;
      }
    }

    info("Processing UUFI inbound message %s/%s", envelope.subType, envelope.subFolder);
    handleUufiMqttInbound(envelope, messageMap);
  }

  private boolean isHandshake(Envelope envelope) {
    return envelope.subType == SubType.STATE && envelope.subFolder == SubFolder.UDMI;
  }

  private UdmiState extractHandshakeState(Envelope envelope, Object message,
      Map<String, Object> messageMap) {
    if (envelope.principal == null && messageMap.get("principal") instanceof String p) {
      envelope.principal = p;
    }
    if (envelope.transactionId == null && messageMap.get("transactionId") instanceof String txn) {
      envelope.transactionId = txn;
    }
    if (messageMap.get("source") instanceof String src && !src.isEmpty()) {
      envelope.source = src;
    }
    Object nestedPayload = messageMap.get(PAYLOAD_KEY);
    if (nestedPayload instanceof Map<?, ?> && !messageMap.containsKey("setup")) {
      return convertTo(UdmiState.class, nestedPayload);
    }
    return convertTo(UdmiState.class, message);
  }

  private void handleHandshake(Envelope envelope, UdmiState state) {
    String source = Optional.ofNullable(state.setup).map(setup -> setup.msg_source)
        .orElse(envelope.source);
    String transactionId = Optional.ofNullable(state.setup).map(setup -> setup.transaction_id)
        .orElse(envelope.transactionId);

    debug("Received UUFI handshake from %s (txn: %s)", source, transactionId);

    Envelope replyEnvelope = new Envelope();
    replyEnvelope.subType = SubType.CONFIG;
    replyEnvelope.subFolder = SubFolder.UDMI;
    replyEnvelope.source = UDMIS_SOURCE;
    replyEnvelope.principal = Optional.ofNullable(envelope.principal).orElse(source);
    replyEnvelope.transactionId = transactionId;
    replyEnvelope.publishTime = CleanDateFormat.cleanDate();

    info("Sending UUFI handshake reply to %s (txn: %s)", source, transactionId);

    UdmiConfig config = UdmiServicePod.getUdmiConfig(state);
    publishUufiMessage(replyEnvelope, config);
  }

  private void handleUufiMqttInbound(Envelope envelope, Map<String, Object> messageMap) {
    Map<String, Object> mutableMap = toMap(messageMap);
    final Object payloadRaw = mutableMap.remove(PAYLOAD_KEY);

    String reqPrincipal = extractString(mutableMap.get("principal"), envelope.principal);
    String reqTxnId = extractString(mutableMap.get("transactionId"), envelope.transactionId);

    if (Strings.isNullOrEmpty(reqPrincipal)) {
      warn("Dropping UUFI MQTT client message without principal for %s/%s",
          envelope.subType, envelope.subFolder);
      return;
    }

    if (hasControlRoot(payloadRaw)) {
      warn("Dropping client-originated UUFI control message for %s/%s (principal=%s, txn=%s)",
          envelope.subType, envelope.subFolder, reqPrincipal, reqTxnId);
      return;
    }

    Envelope replyCoords = deepCopy(envelope);
    try {
      Envelope innerEnvelope = convertTo(Envelope.class, mutableMap);
      boolean hasTopicPath = envelope.subType != null || envelope.subFolder != null;
      if (innerEnvelope.subType == null || innerEnvelope.subFolder == null
          || (envelope.deviceRegistryId != null && innerEnvelope.deviceRegistryId == null)
          || (envelope.deviceId != null && innerEnvelope.deviceId == null)) {
        String msg = String.format("Missing required UUFI envelope fields: "
            + "subType=%s, subFolder=%s, registry=%s, device=%s",
            innerEnvelope.subType, innerEnvelope.subFolder,
            innerEnvelope.deviceRegistryId, innerEnvelope.deviceId);
        throw new IllegalArgumentException(msg);
      }
      boolean registryMismatch = hasTopicPath
          ? !Objects.equals(envelope.deviceRegistryId, innerEnvelope.deviceRegistryId)
          : (envelope.deviceRegistryId != null
              && !Objects.equals(envelope.deviceRegistryId, innerEnvelope.deviceRegistryId));
      boolean deviceMismatch = hasTopicPath
          ? !Objects.equals(envelope.deviceId, innerEnvelope.deviceId)
          : (envelope.deviceId != null
              && !Objects.equals(envelope.deviceId, innerEnvelope.deviceId));
      if ((envelope.subType != null && innerEnvelope.subType != envelope.subType)
          || (envelope.subFolder != null && innerEnvelope.subFolder != envelope.subFolder)
          || registryMismatch || deviceMismatch) {
        String msg = String.format("Mismatch between topic coordinates (%s/%s/%s/%s) "
            + "and inner envelope fields (%s/%s/%s/%s)",
            envelope.deviceRegistryId, envelope.deviceId,
            envelope.subType, envelope.subFolder,
            innerEnvelope.deviceRegistryId, innerEnvelope.deviceId,
            innerEnvelope.subType, innerEnvelope.subFolder);
        throw new IllegalArgumentException(msg);
      }

      if (isExcludedErrorChannel(innerEnvelope)) {
        debug("Dropping excluded error channel %s/%s on UUFI MQTT inner envelope",
            innerEnvelope.subType, innerEnvelope.subFolder);
        return;
      }

      innerEnvelope.payload = null;
      innerEnvelope.gatewayId = null;
      innerEnvelope.principal = reqPrincipal;
      innerEnvelope.transactionId = reqTxnId;
      replyCoords = innerEnvelope;

      Object innerPayload = (payloadRaw instanceof String strPayload)
          ? MessageDispatcher.sentinelPayload(strPayload) : payloadRaw;

      if (hasControlRoot(innerPayload)) {
        warn("Dropping client-originated UUFI control payload for %s/%s (principal=%s, txn=%s)",
            innerEnvelope.subType, innerEnvelope.subFolder, reqPrincipal, reqTxnId);
        return;
      }

      dispatchUufiRequest(innerEnvelope, innerPayload, reqPrincipal, reqTxnId);
    } catch (Exception e) {
      error("Error processing UUFI inbound request: " + friendlyStackTrace(e));
      sendControlError(replyCoords, reqPrincipal, reqTxnId, errorCategoryFor(replyCoords), e);
    }
  }

  private void dispatchUufiRequest(Envelope innerEnvelope, Object innerPayload,
      String reqPrincipal, String reqTxnId) {
    if (innerEnvelope.subType == SubType.QUERY) {
      throw new IllegalArgumentException(
          "Unsupported UUFI subType 'query'; send a null payload on the target channel");
    }

    if (innerPayload == null) {
      handleUufiQuery(innerEnvelope, reqPrincipal, reqTxnId);
      return;
    }

    if (innerPayload instanceof SentinelPayload sentinel) {
      handleStringSentinelWrite(innerEnvelope, sentinel, reqPrincipal, reqTxnId);
      return;
    }

    debug("Forwarding UUFI message %s/%s from %s to internal bus",
        innerEnvelope.subType, innerEnvelope.subFolder, innerEnvelope.source);

    if (innerEnvelope.subType == SubType.MODEL) {
      throw new IllegalArgumentException(
          "UUFI model writes are not supported; use registrar/reflector for model updates");
    }

    if (innerEnvelope.subType == SubType.CONFIG) {
      if (Strings.isNullOrEmpty(innerEnvelope.deviceRegistryId)
          || Strings.isNullOrEmpty(innerEnvelope.deviceId)) {
        throw new IllegalArgumentException(
            "Missing required deviceRegistryId/deviceId for UUFI config write");
      }
      if (!(innerPayload instanceof Map<?, ?>)) {
        throw new IllegalArgumentException(
            "UUFI config write payload must be a JSON object");
      }
      String configUpdate = processConfigChange(innerEnvelope, innerPayload, null);
      if (configUpdate == null) {
        throw new IllegalStateException("UUFI config update failed or produced no result");
      }
      Envelope busEnvelope = deepCopy(innerEnvelope);
      busEnvelope.subFolder = null;
      Map<String, Object> fullConfig = toMap(configUpdate);
      publish(busEnvelope, fullConfig);

      Object ackPayload = extractSubBlockPayload(fullConfig, innerEnvelope.subFolder);
      sendUufiSuccessReply(innerEnvelope, reqPrincipal, reqTxnId, ackPayload);
      return;
    }

    publish(innerEnvelope, innerPayload);
  }

  private void handleStringSentinelWrite(Envelope innerEnvelope, SentinelPayload sentinel,
      String reqPrincipal, String reqTxnId) {
    throw new IllegalArgumentException(
        "Unsupported string sentinel payload '" + sentinel.value + "' for "
            + innerEnvelope.subType + "/" + innerEnvelope.subFolder);
  }

  private void handleUufiQuery(Envelope requestEnvelope, String reqPrincipal, String reqTxnId) {
    String registryId = requestEnvelope.deviceRegistryId;
    String deviceId = requestEnvelope.deviceId;
    if (Strings.isNullOrEmpty(registryId) || Strings.isNullOrEmpty(deviceId)) {
      throw new IllegalArgumentException(
          "Missing required deviceRegistryId/deviceId for UUFI query");
    }

    switch (requestEnvelope.subType) {
      case CONFIG -> {
        Map.Entry<Long, String> entry = iotAccess.fetchConfig(registryId, deviceId);
        Object replyPayload = (entry == null || Strings.isNullOrEmpty(entry.getValue()))
            ? MessageDispatcher.sentinelPayload(MISSING_SENTINEL)
            : extractSubBlockPayload(toMap(entry.getValue()), requestEnvelope.subFolder);
        sendUufiSuccessReply(requestEnvelope, reqPrincipal, reqTxnId, replyPayload);
      }
      case STATE -> {
        String stateJson = iotAccess.fetchState(registryId, deviceId);
        Object replyPayload = Strings.isNullOrEmpty(stateJson)
            ? MessageDispatcher.sentinelPayload(MISSING_SENTINEL)
            : extractSubBlockPayload(toMap(stateJson), requestEnvelope.subFolder);
        sendUufiSuccessReply(requestEnvelope, reqPrincipal, reqTxnId, replyPayload);
      }
      case MODEL -> {
        CloudModel deviceModel = iotAccess.fetchDevice(registryId, deviceId);
        if (deviceModel == null) {
          sendUufiSuccessReply(requestEnvelope, reqPrincipal, reqTxnId,
              MessageDispatcher.sentinelPayload(MISSING_SENTINEL));
          return;
        }
        if (requestEnvelope.subFolder == SubFolder.SYSTEM) {
          String metadataStr = deviceModel.metadata != null
              ? deviceModel.metadata.get("udmi_metadata") : null;
          SystemModel systemModel = metadataStr != null
              ? catchToNull(() -> fromString(Metadata.class, metadataStr).system) : null;
          if (systemModel == null) {
            sendUufiSuccessReply(requestEnvelope, reqPrincipal, reqTxnId,
                MessageDispatcher.sentinelPayload(MISSING_SENTINEL));
            return;
          }
          Envelope replyCoords = deepCopy(requestEnvelope);
          if (replyCoords.deviceGuid == null) {
            replyCoords.deviceGuid = systemModel.guid;
          }
          Map<String, Object> payloadMap = toMap(systemModel);
          payloadMap.put("timestamp", ofNullable(requestEnvelope.publishTime)
              .map(t -> isoConvert(t)).orElseGet(() -> isoConvert()));
          payloadMap.put("version", ofNullable(deviceModel.version).orElse(UDMI_VERSION));
          sendUufiSuccessReply(replyCoords, reqPrincipal, reqTxnId, payloadMap);
        } else if (requestEnvelope.subFolder == SubFolder.CLOUD) {
          Map<String, Object> payloadMap = scrubCloudModelMap(toMap(deviceModel));
          payloadMap.putIfAbsent("timestamp", ofNullable(requestEnvelope.publishTime)
              .map(t -> isoConvert(t)).orElseGet(() -> isoConvert()));
          payloadMap.putIfAbsent("version", ofNullable(deviceModel.version).orElse(UDMI_VERSION));
          sendUufiSuccessReply(requestEnvelope, reqPrincipal, reqTxnId, payloadMap);
        } else {
          throw new IllegalArgumentException(
              "Unsupported model query subFolder: " + requestEnvelope.subFolder);
        }
      }
      default -> throw new IllegalArgumentException(
          "UUFI query not supported for subType: " + requestEnvelope.subType);
    }
  }

  private Object extractSubBlockPayload(Map<String, Object> fullDocument, SubFolder subFolder) {
    if (subFolder == SubFolder.UPDATE) {
      return fullDocument;
    }
    Object subBlock = fullDocument.get(subFolder.value());
    if (subBlock == null) {
      return MessageDispatcher.sentinelPayload(MISSING_SENTINEL);
    }
    Map<String, Object> subMap = toMap(subBlock);
    if (!subMap.containsKey("version") && fullDocument.get("version") != null) {
      subMap.put("version", fullDocument.get("version"));
    }
    if (!subMap.containsKey("timestamp") && fullDocument.get("timestamp") != null) {
      subMap.put("timestamp", fullDocument.get("timestamp"));
    }
    return subMap;
  }

  protected void sendControlProgress(Envelope requestEnvelope, String principal,
      String transactionId, String category, String message) {
    sendControlReply(requestEnvelope, principal, transactionId, category,
        Level.INFO.value(), message, null);
  }

  private void sendControlError(Envelope requestEnvelope, String principal,
      String transactionId, String category, Exception e) {
    String message = ofNullable(Common.getExceptionMessage(e))
        .orElse(e.getClass().getSimpleName());
    String detail = friendlyStackTrace(e);
    sendControlReply(requestEnvelope, principal, transactionId, category,
        Level.ERROR.value(), message, detail);
  }

  private void sendControlReply(Envelope requestEnvelope, String principal,
      String transactionId, String category, int level, String message, String detail) {
    if (Strings.isNullOrEmpty(principal)) {
      debug("Suppressing UUFI control reply because principal is absent");
      return;
    }
    if (!pubSubProtocol
        && (requestEnvelope == null || requestEnvelope.subType == null
            || requestEnvelope.subFolder == null)) {
      warn("Cannot send UUFI MQTT control reply without subType/subFolder coordinates");
      return;
    }
    final Date now = CleanDateFormat.cleanDate();
    Entry status = new Entry();
    status.message = message;
    status.detail = detail;
    status.category = category;
    status.level = level;
    status.timestamp = now;

    UufiControl control = new UufiControl();
    control.timestamp = now;
    control.version = UDMI_VERSION;
    control.control = new UufiControlBlock();
    control.control.status = status;

    Envelope replyEnvelope = makeReplyEnvelope(requestEnvelope, principal, transactionId);
    publishUufiMessage(replyEnvelope, control);
  }

  private void sendUufiSuccessReply(Envelope requestEnvelope, String principal,
      String transactionId, Object payload) {
    Envelope replyEnvelope = makeReplyEnvelope(requestEnvelope, principal, transactionId);
    publishUufiMessage(replyEnvelope, payload);
  }

  private Envelope makeReplyEnvelope(Envelope requestEnvelope, String principal,
      String transactionId) {
    Envelope reply = new Envelope();
    if (requestEnvelope != null) {
      reply.subType = requestEnvelope.subType;
      reply.subFolder = requestEnvelope.subFolder;
      reply.deviceRegistryId = requestEnvelope.deviceRegistryId;
      reply.deviceId = requestEnvelope.deviceId;
      reply.deviceGuid = requestEnvelope.deviceGuid;
      reply.projectId = requestEnvelope.projectId;
    }
    reply.source = UDMIS_SOURCE;
    reply.principal = principal;
    reply.transactionId = transactionId;
    reply.publishTime = CleanDateFormat.cleanDate();
    return reply;
  }

  private void publishUufiMessage(Envelope uufiEnvelope, Object payload) {
    if (isExcludedErrorChannel(uufiEnvelope)) {
      debug("Dropping excluded error channel %s/%s from UUFI publish",
          uufiEnvelope.subType, uufiEnvelope.subFolder);
      return;
    }
    uufiEnvelope.source = UDMIS_SOURCE;
    if (uufiEnvelope.publishTime == null) {
      uufiEnvelope.publishTime = CleanDateFormat.cleanDate();
    }
    if (pubSubProtocol) {
      uufiEnvelope.gatewayId = null;
      publishSidecarOrMain(uufiEnvelope, payload);
    } else {
      uufiEnvelope.gatewayId = UUFI_GATEWAY_ID;
      Map<String, Object> wrapper = toMap(uufiEnvelope);
      wrapper.remove("gatewayId");
      wrapper.put("source", UDMIS_SOURCE);
      if (uufiEnvelope.principal != null) {
        wrapper.put("principal", uufiEnvelope.principal);
      } else {
        wrapper.remove("principal");
      }
      Object wirePayload = payload instanceof SentinelPayload sentinel
          ? sentinel.value : payload;
      wrapper.put(PAYLOAD_KEY, wirePayload);
      publishSidecarOrMain(uufiEnvelope, wrapper);
    }
  }

  private void handleUufiOutbound(Envelope envelope, Object message) {
    if (isExcludedErrorChannel(envelope)) {
      debug("Dropping excluded error channel %s/%s from UUFI outbound",
          envelope.subType, envelope.subFolder);
      return;
    }

    boolean isStateUpdate = envelope.subType == SubType.STATE
        && (envelope.subFolder == null || envelope.subFolder == SubFolder.UPDATE);
    if (isStateUpdate) {
      try {
        StateUpdate stateUpdate = convertTo(StateUpdate.class, message);
        Arrays.stream(udmi.schema.State.class.getFields()).forEach(field -> {
          try {
            String fieldName = field.getName();
            Object fieldMessage = field.get(stateUpdate);
            if (fieldMessage != null) {
              SubFolder subFolder = catchToNull(() -> SubFolder.fromValue(fieldName));
              if (subFolder != null && subFolder != SubFolder.ERROR) {
                Map<String, Object> shardedPayload = toMap(fieldMessage);
                shardedPayload.put("version", stateUpdate.version);
                shardedPayload.put("timestamp", stateUpdate.timestamp);

                Envelope shardedEnvelope = deepCopy(envelope);
                shardedEnvelope.subFolder = subFolder;

                handleUufiOutbound(shardedEnvelope, shardedPayload);
              }
            }
          } catch (Exception e) {
            error("Error sharding outbound state field " + field.getName() + ": "
                + friendlyStackTrace(e));
          }
        });
        return;
      } catch (Exception e) {
        error("Error processing monolithic outbound state update: " + friendlyStackTrace(e));
      }
    }

    if (envelope.subFolder == null) {
      debug("Dropping outbound message with null subFolder for subType=%s", envelope.subType);
      return;
    }

    if (UUFI_EVENTS_CONTAINER_ID.equals(containerId)) {
      boolean allowedTargetBroadcast = envelope.subType == SubType.EVENTS
          || (envelope.subType == SubType.MODEL
              && envelope.subFolder == SubFolder.SYSTEM
              && Strings.isNullOrEmpty(envelope.principal));
      if (!allowedTargetBroadcast) {
        debug("Dropping non-broadcast message %s/%s (principal=%s) on %s",
            envelope.subType, envelope.subFolder, envelope.principal, containerId);
        return;
      }
    }

    Object outboundMessage = message;

    if (envelope.deviceGuid == null && message instanceof SystemModel systemModel) {
      envelope.deviceGuid = systemModel.guid;
    } else if (envelope.deviceGuid == null && message instanceof CloudModel cloudModel) {
      envelope.deviceGuid = catchToNull(() -> fromString(
          Metadata.class, cloudModel.metadata.get("udmi_metadata")).system.guid);
    }

    if (envelope.subFolder == SubFolder.SYSTEM && message instanceof CloudModel cloudModel) {
      String metadataStr = (cloudModel.metadata != null)
          ? cloudModel.metadata.get("udmi_metadata") : null;
      SystemModel systemModel = null;
      if (metadataStr != null) {
        Metadata metadata = fromString(Metadata.class, metadataStr);
        systemModel = metadata.system;
      }
      if (systemModel == null) {
        systemModel = new SystemModel();
      }
      Map<String, Object> payloadMap = toMap(systemModel);
      payloadMap.put("timestamp", envelope.publishTime);
      payloadMap.put("version", Optional.ofNullable(cloudModel.version).orElse(UDMI_VERSION));
      outboundMessage = payloadMap;
    }

    Envelope uufiEnvelope = new Envelope();
    uufiEnvelope.subType = envelope.subType;
    uufiEnvelope.subFolder = envelope.subFolder;
    uufiEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    uufiEnvelope.deviceId = envelope.deviceId;
    uufiEnvelope.deviceGuid = envelope.deviceGuid;
    uufiEnvelope.projectId = envelope.projectId;
    uufiEnvelope.transactionId = envelope.transactionId;
    uufiEnvelope.publishTime = envelope.publishTime;
    uufiEnvelope.source = UDMIS_SOURCE;
    boolean isBroadcastStream = envelope.subType == SubType.EVENTS
        || envelope.subType == SubType.STATE
        || UdmiServicePod.INSTANCE_ID.equals(envelope.principal);
    uufiEnvelope.principal = isBroadcastStream ? null : envelope.principal;

    debug("Forwarding system message %s/%s to UUFI clients",
        envelope.subType, envelope.subFolder);
    publishUufiMessage(uufiEnvelope, outboundMessage);
  }
}
