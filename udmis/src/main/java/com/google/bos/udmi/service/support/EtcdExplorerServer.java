package com.google.bos.udmi.service.support;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.IotAccess;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.PodConfiguration;

/**
 * Lightweight web server and REST API for exploring ETCD hierarchical data.
 */
public class EtcdExplorerServer {

  private static final Pattern DEVICES_PATTERN =
      Pattern.compile("^/api/registries/([^/]+)/devices/?$");
  private static final Pattern PROPERTIES_PATTERN =
      Pattern.compile("^/api/registries/([^/]+)/devices/([^/]+)/properties/?$");

  private final HttpServer server;
  private final EtcdDataProvider etcdProvider;
  private final int port;

  /**
   * Create an instance of the EtcdExplorerServer.
   */
  public EtcdExplorerServer(int port, EtcdDataProvider etcdProvider) {
    try {
      this.etcdProvider = etcdProvider;
      this.server = HttpServer.create(new InetSocketAddress(port), 0);
      this.port = server.getAddress().getPort();
      registerHandlers();
    } catch (Exception e) {
      throw new RuntimeException("While initializing EtcdExplorerServer on port " + port, e);
    }
  }

  private void registerHandlers() {
    server.createContext("/api/registries", this::handleRegistriesRouter);
    server.createContext("/", this::handleStatic);
  }

  private void handleRegistriesRouter(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendResponse(exchange, HttpURLConnection.HTTP_BAD_METHOD, "application/json",
          JsonUtil.stringify(Map.of("error", "Only GET method is supported")));
      return;
    }

    String path = exchange.getRequestURI().getPath();
    try {
      if ("/api/registries".equals(path) || "/api/registries/".equals(path)) {
        handleGetRegistries(exchange);
        return;
      }

      Matcher devicesMatcher = DEVICES_PATTERN.matcher(path);
      if (devicesMatcher.matches()) {
        handleGetDevices(exchange, devicesMatcher.group(1));
        return;
      }

      Matcher propertiesMatcher = PROPERTIES_PATTERN.matcher(path);
      if (propertiesMatcher.matches()) {
        handleGetProperties(exchange, propertiesMatcher.group(1), propertiesMatcher.group(2));
        return;
      }

      sendResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "application/json",
          JsonUtil.stringify(Map.of("error", "Endpoint not found: " + path)));
    } catch (Exception e) {
      System.err.println("Error handling request " + path + ": " + friendlyStackTrace(e));
      sendResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "application/json",
          JsonUtil.stringify(Map.of("error", friendlyStackTrace(e))));
    }
  }

  private void handleGetRegistries(HttpExchange exchange) throws IOException {
    List<String> keys = etcdProvider.getPrefixKeys("/r/");
    Set<String> uniqueRegistries = new TreeSet<>();
    Set<String> uniqueDevices = new TreeSet<>();
    for (String key : keys) {
      if (key.startsWith("/r/")) {
        String sub = key.substring(3);
        int slashIdx = sub.indexOf('/');
        int colonIdx = sub.indexOf(':');
        int endIdx = slashIdx >= 0 && colonIdx >= 0 ? Math.min(slashIdx, colonIdx)
            : Math.max(slashIdx, colonIdx);
        String reg = endIdx >= 0 ? sub.substring(0, endIdx) : sub;
        if (!reg.isEmpty()) {
          uniqueRegistries.add(reg);
        }
        int deviceIndex = sub.indexOf("/d/");
        if (deviceIndex >= 0) {
          String devSub = sub.substring(deviceIndex + 3);
          int devSlashIdx = devSub.indexOf('/');
          int devColonIdx = devSub.indexOf(':');
          int devEndIdx = devSlashIdx >= 0 && devColonIdx >= 0 ? Math.min(devSlashIdx, devColonIdx)
              : Math.max(devSlashIdx, devColonIdx);
          String dev = devEndIdx >= 0 ? devSub.substring(0, devEndIdx) : devSub;
          if (!reg.isEmpty() && !dev.isEmpty()) {
            uniqueDevices.add(reg + "/" + dev);
          }
        }
      }
    }
    sendResponse(exchange, HttpURLConnection.HTTP_OK, "application/json",
        JsonUtil.stringify(Map.of("registries", new ArrayList<>(uniqueRegistries),
            "totalDevicesCount", uniqueDevices.size())));
  }

  private void handleGetDevices(HttpExchange exchange, String registryId) throws IOException {
    String prefix = "/r/" + registryId + "/d/";
    List<String> keys = etcdProvider.getPrefixKeys(prefix);
    Set<String> uniqueDevices = new TreeSet<>();
    for (String key : keys) {
      if (key.startsWith(prefix)) {
        String sub = key.substring(prefix.length());
        int slashIdx = sub.indexOf('/');
        int colonIdx = sub.indexOf(':');
        int endIdx = slashIdx >= 0 && colonIdx >= 0 ? Math.min(slashIdx, colonIdx)
            : Math.max(slashIdx, colonIdx);
        String dev = endIdx >= 0 ? sub.substring(0, endIdx) : sub;
        if (!dev.isEmpty()) {
          uniqueDevices.add(dev);
        }
      }
    }
    sendResponse(exchange, HttpURLConnection.HTTP_OK, "application/json",
        JsonUtil.stringify(
            Map.of("registryId", registryId, "devices", new ArrayList<>(uniqueDevices))));
  }

  private void handleGetProperties(HttpExchange exchange, String registryId, String deviceId)
      throws IOException {
    String prefix = "/r/" + registryId + "/d/" + deviceId;
    Map<String, String> entries = new TreeMap<>();
    entries.putAll(etcdProvider.getPrefixEntries(prefix + ":"));
    entries.putAll(etcdProvider.getPrefixEntries(prefix + "/"));
    String exactValue = etcdProvider.getEntry(prefix);
    if (exactValue != null) {
      entries.put(prefix, exactValue);
    }
    Map<String, String> properties = new TreeMap<>();
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(prefix)) {
        String propKey = key.substring(prefix.length());
        if (propKey.isEmpty()) {
          propKey = ":value";
        }
        properties.put(propKey, entry.getValue());
      }
    }
    sendResponse(exchange, HttpURLConnection.HTTP_OK, "application/json",
        JsonUtil.stringify(Map.of("registryId", registryId, "deviceId", deviceId,
            "properties", properties)));
  }

  private void handleStatic(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendResponse(exchange, HttpURLConnection.HTTP_BAD_METHOD, "text/plain", "Method not allowed");
      return;
    }
    String path = exchange.getRequestURI().getPath();
    if (path.equals("/") || path.isEmpty()) {
      path = "/index.html";
    }
    InputStream resourceStream =
        EtcdExplorerServer.class.getResourceAsStream("/etcd_explorer" + path);
    if (resourceStream == null) {
      sendResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "text/plain",
          "404 Not Found: " + path);

      return;
    }
    byte[] content = resourceStream.readAllBytes();
    resourceStream.close();

    String contentType = getContentType(path);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, content.length);
    OutputStream os = exchange.getResponseBody();
    os.write(content);
    os.close();
  }

  private String getContentType(String path) {
    if (path.endsWith(".html")) {
      return "text/html; charset=utf-8";
    } else if (path.endsWith(".css")) {
      return "text/css; charset=utf-8";
    } else if (path.endsWith(".js")) {
      return "application/javascript; charset=utf-8";
    } else if (path.endsWith(".json")) {
      return "application/json; charset=utf-8";
    }
    return "application/octet-stream";
  }

  private void sendResponse(HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    OutputStream os = exchange.getResponseBody();
    os.write(bytes);
    os.close();
  }

  /**
   * Start the web server.
   */
  public void start() {
    server.start();
    System.err.println("EtcdExplorerServer started on port " + port);
  }

  /**
   * Stop the web server.
   */
  public void stop() {
    server.stop(0);
    System.err.println("EtcdExplorerServer stopped.");
  }

  public int getPort() {
    return port;
  }

  /**
   * Main entrypoint for CLI launcher.
   */
  public static void main(String[] args) {
    Map<String, String> parsedArgs = parseArgs(args);
    String configPath = parsedArgs.get("config");
    if (configPath == null && args.length > 0) {
      for (String arg : args) {
        if (!arg.startsWith("-") && new File(arg).exists()) {
          configPath = arg;
          break;
        }
      }
    }

    EtcdDataProvider provider;
    if (configPath != null) {
      PodConfiguration config = UdmiServicePod.loadRecursive(new File(configPath));
      IotAccess etcdAccess = null;
      if (config.iot_data != null) {
        for (IotAccess access : config.iot_data.values()) {
          if (IotProvider.ETCD.equals(access.provider)) {
            etcdAccess = access;
            break;
          }
        }
      }
      if (etcdAccess == null && config.iot_access != null) {
        for (IotAccess access : config.iot_access.values()) {
          if (IotProvider.ETCD.equals(access.provider)) {
            etcdAccess = access;
            break;
          }
        }
      }
      if (etcdAccess == null) {
        throw new RuntimeException("No etcd provider configuration found in " + configPath);
      }
      provider = (EtcdDataProvider) IotDataProvider.from(etcdAccess);
    } else {
      String etcdOptions = parsedArgs.getOrDefault("etcd_options", "");
      StringBuilder optionsBuilder = new StringBuilder(etcdOptions);
      appendOption(optionsBuilder, "ca_file", parsedArgs.get("etcd_ca_path"));
      appendOption(optionsBuilder, "cert_file", parsedArgs.get("etcd_client_cert_path"));
      appendOption(optionsBuilder, "key_file", parsedArgs.get("etcd_client_key_path"));

      IotAccess iotAccess = new IotAccess();
      iotAccess.provider = IotProvider.ETCD;
      iotAccess.project_id = parsedArgs.getOrDefault("etcd_target", "http://localhost:2379");
      iotAccess.options = optionsBuilder.length() > 0 ? optionsBuilder.toString() : null;
      provider = new EtcdDataProvider(iotAccess);
    }

    int port = Integer.parseInt(parsedArgs.getOrDefault("port", "8080"));
    EtcdExplorerServer server = new EtcdExplorerServer(port, provider);
    server.start();
  }

  private static void appendOption(StringBuilder builder, String key, String value) {
    if (value != null && !value.trim().isEmpty()) {
      if (builder.length() > 0) {
        builder.append(",");
      }
      builder.append(key).append("=").append(value.trim());
    }
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> map = new HashMap<>();
    for (String arg : args) {
      if (arg.startsWith("--")) {
        String clean = arg.substring(2);
        int idx = clean.indexOf('=');
        if (idx > 0) {
          map.put(clean.substring(0, idx), clean.substring(idx + 1));
        } else {
          map.put(clean, "true");
        }
      } else if (arg.startsWith("-")) {
        String clean = arg.substring(1);
        int idx = clean.indexOf('=');
        if (idx > 0) {
          map.put(clean.substring(0, idx), clean.substring(idx + 1));
        } else {
          map.put(clean, "true");
        }
      }
    }
    return map;
  }
}
