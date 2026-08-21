package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * UDMIS component that captures incoming message streams and stores pointset events to InfluxDB
 * and all other messages to PostgreSQL.
 */
@ComponentName("capture")
public class CaptureProcessor extends ProcessorBase {

  private final String postgresHost;
  private final String postgresPort;
  private final String postgresUser;
  private final String postgresPassword;
  private final String postgresDb;

  private final String influxHost;
  private final String influxPort;
  private final String influxToken;
  private final String influxOrg;
  private final String influxBucket;

  private final HttpClient httpClient;

  /**
   * Construct a new CaptureProcessor component.
   */
  public CaptureProcessor(EndpointConfiguration config) {
    super(config);

    postgresHost = getEnvOrDefault("POSTGRES_HOST", "127.0.0.1");
    postgresPort = getEnvOrDefault("POSTGRES_PORT", "5432");
    postgresUser = getEnvOrDefault("POSTGRES_USER", "postgres");
    postgresPassword = getEnvOrDefault("POSTGRES_PASSWORD", "");
    postgresDb = getEnvOrDefault("POSTGRES_DB", "postgres");

    influxHost = getEnvOrDefault("INFLUXDB_HOST", "127.0.0.1");
    influxPort = getEnvOrDefault("INFLUXDB_PORT", getEnvOrDefault("INFLUX_PORT", "8086"));
    influxToken = getEnvOrDefault("INFLUXDB_TOKEN", "test-influx-token-12345");
    influxOrg = getEnvOrDefault("INFLUXDB_ORG", "bridgehead");
    influxBucket = getEnvOrDefault("INFLUXDB_BUCKET", "home");

    httpClient = HttpClient.newHttpClient();

    registerHandler(udmi.schema.PointsetEvents.class, this::pointsetEventsHandler);

    initPostgres();
  }

  private void pointsetEventsHandler(udmi.schema.PointsetEvents events) {
    MessageContinuation continuation = getContinuation(events);
    Envelope envelope = continuation.getEnvelope();
    saveToInflux(envelope, events);
  }

  private static String getEnvOrDefault(String key, String defaultValue) {
    String val = System.getenv(key);
    return (val != null && !val.isEmpty()) ? val : defaultValue;
  }

  private Connection getPostgresConnection() throws SQLException {
    String url = String.format("jdbc:postgresql://%s:%s/%s",
        postgresHost, postgresPort, postgresDb);
    if (postgresUser != null && !postgresUser.isEmpty()) {
      return DriverManager.getConnection(url, postgresUser, postgresPassword);
    }
    return DriverManager.getConnection(url);
  }

  private void initPostgres() {
    String[] sqlStatements = new String[] {
        "CREATE TABLE IF NOT EXISTS udmi_messages ("
            + "id SERIAL PRIMARY KEY, "
            + "project_id VARCHAR(255), "
            + "registry_id VARCHAR(255), "
            + "device_id VARCHAR(255), "
            + "sub_folder VARCHAR(50), "
            + "sub_type VARCHAR(50), "
            + "publish_time TIMESTAMP, "
            + "payload JSONB, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_point_state ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "device_id VARCHAR(255), "
            + "device_registry_id VARCHAR(255), "
            + "message_id VARCHAR(255), "
            + "point_name VARCHAR(255), "
            + "value_state VARCHAR(50), "
            + "units VARCHAR(50), "
            + "status_timestamp TIMESTAMP, "
            + "level INTEGER, "
            + "category VARCHAR(100), "
            + "message TEXT, "
            + "detail TEXT, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_system_state ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "publish_timestamp TIMESTAMP, "
            + "device_registry_id VARCHAR(255), "
            + "device_id VARCHAR(255), "
            + "device_num_id VARCHAR(255), "
            + "gateway_id VARCHAR(255), "
            + "make VARCHAR(255), "
            + "model VARCHAR(255), "
            + "serial_no VARCHAR(255), "
            + "rev VARCHAR(255), "
            + "sku VARCHAR(255), "
            + "software JSONB, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_discovery ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "generation TIMESTAMP, "
            + "device_registry_id VARCHAR(255), "
            + "device_id VARCHAR(255), "
            + "message_id VARCHAR(255), "
            + "scan_family VARCHAR(50), "
            + "ether_addr VARCHAR(255), "
            + "ipv4_addr VARCHAR(255), "
            + "bacnet_addr VARCHAR(255), "
            + "hostname VARCHAR(255), "
            + "fqdn VARCHAR(255), "
            + "hardware_make VARCHAR(255), "
            + "hardware_model VARCHAR(255), "
            + "firmware_version VARCHAR(255), "
            + "serial_no VARCHAR(255), "
            + "status_level INTEGER, "
            + "status_category VARCHAR(100), "
            + "status_message TEXT, "
            + "ports JSONB, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_validation ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "device_registry_id VARCHAR(255), "
            + "device_id VARCHAR(255), "
            + "message_type VARCHAR(100), "
            + "message TEXT, "
            + "detail TEXT, "
            + "category VARCHAR(100), "
            + "level INTEGER, "
            + "errors JSONB, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_alarms ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "device_registry_id VARCHAR(255), "
            + "device_id VARCHAR(255), "
            + "alarm_category VARCHAR(100), "
            + "alarm_priority VARCHAR(50), "
            + "alarm_type VARCHAR(100), "
            + "controller VARCHAR(255), "
            + "equipment VARCHAR(255), "
            + "fault BOOLEAN, "
            + "from_state VARCHAR(50), "
            + "to_state VARCHAR(50), "
            + "generation_time TIMESTAMP, "
            + "in_alarm BOOLEAN, "
            + "location_path VARCHAR(255), "
            + "message_text TEXT, "
            + "out_of_service BOOLEAN, "
            + "overridden BOOLEAN, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");",
        "CREATE TABLE IF NOT EXISTS udmi_metadata ("
            + "id SERIAL PRIMARY KEY, "
            + "timestamp TIMESTAMP, "
            + "device_id VARCHAR(255), "
            + "device_registry_id VARCHAR(255), "
            + "system_hardware_make VARCHAR(255), "
            + "system_hardware_model VARCHAR(255), "
            + "system_hardware_sku VARCHAR(255), "
            + "system_hardware_rev VARCHAR(255), "
            + "system_location_room VARCHAR(255), "
            + "system_location_floor VARCHAR(255), "
            + "localnet_families_ipv4_addr VARCHAR(255), "
            + "localnet_families_ether_addr VARCHAR(255), "
            + "cloud_connection_type VARCHAR(50), "
            + "metadata JSONB, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");"
    };
    try (Connection conn = getPostgresConnection();
         Statement stmt = conn.createStatement()) {
      for (String sql : sqlStatements) {
        stmt.execute(sql);
      }
      info("Initialized PostgreSQL standard UDMI tables.");
    } catch (Exception e) {
      warn("Failed to initialize PostgreSQL tables: " + friendlyStackTrace(e));
    }
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    Object payload = defaultedMessage;

    boolean isPointsetEvents = (envelope.subFolder == SubFolder.POINTSET
        && envelope.subType == SubType.EVENTS)
        || (defaultedMessage instanceof udmi.schema.PointsetEvents);

    if (isPointsetEvents) {
      saveToInflux(envelope, payload);
    } else {
      saveToPostgres(envelope, payload);
    }
  }

  /**
   * Save message envelope and payload into PostgreSQL table.
   */
  public void saveToPostgres(Envelope envelope, Object payload) {
    String sql = "INSERT INTO udmi_messages ("
        + "project_id, registry_id, device_id, sub_folder, sub_type, publish_time, payload"
        + ") VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)";
    try (Connection conn = getPostgresConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, envelope.projectId);
      stmt.setString(2, envelope.deviceRegistryId);
      stmt.setString(3, envelope.deviceId);
      stmt.setString(4, envelope.subFolder != null ? envelope.subFolder.value() : null);
      stmt.setString(5, envelope.subType != null ? envelope.subType.value() : null);
      stmt.setTimestamp(6, envelope.publishTime != null
          ? new Timestamp(envelope.publishTime.getTime())
          : null);
      stmt.setString(7, stringify(payload));
      stmt.executeUpdate();
    } catch (Exception e) {
      error("Error saving message to PostgreSQL: " + friendlyStackTrace(e));
    }

    if (envelope.subFolder == SubFolder.SYSTEM && envelope.subType == SubType.STATE) {
      saveSystemState(envelope, payload);
    }
  }

  private void saveSystemState(Envelope envelope, Object payload) {
    String sql = "INSERT INTO udmi_system_state ("
        + "timestamp, publish_timestamp, device_registry_id, device_id, device_num_id, gateway_id, "
        + "make, model, serial_no, rev, sku, software"
        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";
    try (Connection conn = getPostgresConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      Map<String, Object> map = toMap(payload);
      @SuppressWarnings("unchecked")
      Map<String, Object> hw = (Map<String, Object>) map.getOrDefault("hardware", Map.of());
      @SuppressWarnings("unchecked")
      Map<String, Object> sw = (Map<String, Object>) map.getOrDefault("software", Map.of());
      Timestamp ts = envelope.publishTime != null
          ? new Timestamp(envelope.publishTime.getTime())
          : null;
      stmt.setTimestamp(1, ts);
      stmt.setTimestamp(2, ts);
      stmt.setString(3, envelope.deviceRegistryId);
      stmt.setString(4, envelope.deviceId);
      stmt.setString(5, envelope.deviceNumId);
      stmt.setString(6, envelope.gatewayId);
      stmt.setString(7, (String) hw.get("make"));
      stmt.setString(8, (String) hw.get("model"));
      stmt.setString(9, (String) map.get("serial_no"));
      stmt.setString(10, (String) hw.get("rev"));
      stmt.setString(11, (String) hw.get("sku"));
      stmt.setString(12, stringify(sw));
      stmt.executeUpdate();
    } catch (Exception e) {
      debug("Could not insert into udmi_system_state: " + friendlyStackTrace(e));
    }
  }

  /**
   * Save telemetry pointset event metrics into InfluxDB via line protocol.
   */
  public void saveToInflux(Envelope envelope, Object payload) {
    String projectId = envelope.projectId != null ? envelope.projectId : "unknown";
    String registryId = envelope.deviceRegistryId != null ? envelope.deviceRegistryId : "unknown";
    String deviceId = envelope.deviceId != null ? envelope.deviceId : "unknown";
    Date publishTime = envelope.publishTime;

    long tsNs = (publishTime != null)
        ? publishTime.getTime() * 1_000_000L
        : System.currentTimeMillis() * 1_000_000L;

    Map<String, Object> payloadMap = toMap(payload);
    Object pointsObj = payloadMap.get("points");
    if (!(pointsObj instanceof Map)) {
      return;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> points = (Map<String, Object>) pointsObj;
    List<String> lines = new ArrayList<>();

    for (Map.Entry<String, Object> entry : points.entrySet()) {
      Object pointDefObj = entry.getValue();
      if (!(pointDefObj instanceof Map)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> pointDef = (Map<String, Object>) pointDefObj;
      Object presentValue = pointDef.get("present_value");
      if (presentValue == null) {
        presentValue = pointDef.get("presentValue");
      }
      if (presentValue == null) {
        continue;
      }

      String pointName = entry.getKey();
      String valStr;
      String fieldName;
      if (presentValue instanceof Boolean) {
        valStr = (Boolean) presentValue ? "true" : "false";
        fieldName = "present_value_bool";
      } else if (presentValue instanceof Number) {
        valStr = presentValue.toString();
        fieldName = "present_value_num";
      } else {
        valStr = "\"" + presentValue.toString().replace("\"", "\\\"") + "\"";
        fieldName = "present_value_str";
      }

      String line = String.format(
          "point_value,device_id=%s,registry_id=%s,project_id=%s,point_name=%s %s=%s %d",
          deviceId, registryId, projectId, pointName, fieldName, valStr, tsNs);
      lines.add(line);
    }

    if (lines.isEmpty()) {
      return;
    }

    String lineProtocolData = String.join("\n", lines);
    info(String.format("Writing %d point_value metrics to InfluxDB for device %s",
        lines.size(), deviceId));
    writeToInfluxHttp(lineProtocolData);
  }

  private void writeToInfluxHttp(String lineProtocolData) {
    try {
      String urlStr = String.format("http://%s:%s/api/v2/write?org=%s&bucket=%s&precision=ns",
          influxHost, influxPort, influxOrg, influxBucket);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(urlStr))
          .header("Authorization", "Token " + influxToken)
          .header("Content-Type", "text/plain; charset=utf-8")
          .POST(HttpRequest.BodyPublishers.ofString(lineProtocolData))
          .build();
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        error(String.format("InfluxDB write failed with status %d: %s",
            response.statusCode(), response.body()));
      } else {
        info(String.format("InfluxDB write succeeded with status %d", response.statusCode()));
      }
    } catch (Exception e) {
      error("Error writing to InfluxDB: " + friendlyStackTrace(e));
    }
  }
}
