package com.google.bos.iot.core.reconcile;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.JsonUtil.flattenNestedMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Common utils for processing the pub sub messages received from the source repository for
 * ref update events.
 */
public class SourceRepoMessageUtils {

  public static final String REF_UPDATE_EVENT_FORMAT =
      "refUpdateEvent.refUpdates.refs/heads/%s.%s";
  public static final String REPO_NAME_KEY = "name";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Parse the received message to get a flat map.
   *
   * @param message Pub Sub message from source repository
   */
  public static Map<String, Object> parseSourceRepoMessageData(PubsubMessage message)
      throws IOException {
    String messageJson = message.getData().toString(StandardCharsets.UTF_8);
    TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
    };
    Map<String, Object> rawMap = OBJECT_MAPPER.readValue(messageJson, typeRef);
    return flattenNestedMap(rawMap, ".");
  }

  /**
   * Safely retrieves a non-empty string value from a map for a given key.
   *
   * @param stringObjectMap The map to retrieve the value from.
   * @param key The key corresponding to the desired value.
   * @return An {@code Optional<String>} containing the value if it's found and is a non-empty
   *     string. Returns {@code Optional.empty()} if the map is null, the key is not found, the
   *     value is null, the value is not a String, or the value is an empty string.
   */
  public static Optional<String> getValueFromMap(Map<String, Object> stringObjectMap, String key) {
    String result = ifNotNullGet(stringObjectMap, map -> {
      Object value = map.get(key);
      if (value instanceof String s && !s.isEmpty()) {
        return s;
      }
      return null;
    });
    return Optional.ofNullable(result);
  }

  /**
   * Extract repository id from pub sub message data received from the source repository.
   *
   * @return repository id e.g. ZZ-TRI-FECTA
   */
  public static String extractRepoId(Map<String, Object> data) {
    String repositoryName = (String) data.get(REPO_NAME_KEY);
    if (repositoryName == null || repositoryName.isEmpty()) {
      return null;
    }
    String[] parts = repositoryName.split("/");
    return parts[parts.length - 1];
  }

}
