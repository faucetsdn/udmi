package daq.pubber;

public class MqttTopicFactory {

  private static final String TOPIC_PREFIX_FMT = "/devices/%s";
  private static final String ATTACH_TOPIC_FMT = TOPIC_PREFIX_FMT + "/attach";
  private static final String CONFIG_TOPIC_FMT = TOPIC_PREFIX_FMT + "/config";
  private static final String ERRORS_TOPIC_FMT = TOPIC_PREFIX_FMT + "/errors";
  private static final String MESSAGE_TOPIC_FMT = TOPIC_PREFIX_FMT + "/%s";

  static String getEventsSuffix(String subFolder) {
    return "events/" + subFolder;
  }

  static String getStateSuffix() {
    return "state";
  }

  static String getMessageTopic(String deviceId, String topic) {
    return String.format(MESSAGE_TOPIC_FMT, deviceId, topic);
  }

  static String getErrorsTopic(String deviceId) {
    return String.format(ERRORS_TOPIC_FMT, deviceId);
  }

  static String getConfigTopic(String deviceId) {
    return String.format(CONFIG_TOPIC_FMT, deviceId);
  }

  static String getAttachTopic(String deviceId) {
    return String.format(ATTACH_TOPIC_FMT, deviceId);
  }
}
