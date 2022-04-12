package com.google.daq.mqtt.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class FirestoreDataSink implements DataSink {

  private static final String
      VIEW_URL_FORMAT = "https://console.cloud.google.com/firestore/data/registries/?project=%s";

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

  private final Firestore db;

  private final AtomicReference<RuntimeException> oldError = new AtomicReference<>();
  private final String projectId;

  public FirestoreDataSink(String projectId) {
    this.projectId = projectId;
    try {
      GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
      FirestoreOptions firestoreOptions =
          FirestoreOptions.getDefaultInstance().toBuilder()
              .setCredentials(credential)
              .setProjectId(projectId)
              .setTimestampsInSnapshotsEnabled(true)
              .build();

      db = firestoreOptions.getService();
    } catch (Exception e) {
      throw new RuntimeException("While creating Firestore connection to " + projectId, e);
    }
  }

  @Override
  public void validationResult(String deviceId, String schemaId, Map<String, String> attributes,
      Object message, ErrorTree errorTree) {
    if (oldError.get() != null) {
      throw oldError.getAndSet(null);
    }

    try {
      String registryId = attributes.get("deviceRegistryId");
      Preconditions.checkNotNull(deviceId, "deviceId attribute not defined");
      Preconditions.checkNotNull(schemaId, "schemaId not properly defined");
      Preconditions.checkNotNull(registryId, "deviceRegistryId attribute not defined");
      String instantNow = dateTimeFormatter.format(Instant.now());
      DocumentReference registryDoc = db.collection("registries").document(registryId);
      registryDoc.update("validated", instantNow);
      DocumentReference deviceDoc = registryDoc.collection("devices").document(deviceId);
      deviceDoc.update("validated", instantNow);
      PojoBundle dataBundle = new PojoBundle();
      dataBundle.validated = instantNow;
      dataBundle.errorTree = errorTree;
      dataBundle.attributes = attributes;
      dataBundle.message = message;
      DocumentReference resultDoc = deviceDoc.collection("validations").document(schemaId);
      resultDoc.set(dataBundle);
    } catch (Exception e) {
      throw new RuntimeException("While writing result for " + deviceId, e);
    }
  }

  static class PojoBundle {
    public String validated;
    public ErrorTree errorTree;
    public Object message;
    public Map<String, String> attributes;
  }

  public String getViewUrl() {
    return String.format(VIEW_URL_FORMAT, projectId);
  }
}
