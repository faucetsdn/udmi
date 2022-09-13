package com.google.daq.mqtt.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

/**
 * Simple web server used as an endpoint for a cron scheduling job.
 */
public abstract class WebServerBase implements HttpHandler {

  public static final String SITE_PREFIX = "sites/";
  public static final String SCAN_LIMIT = "10m";
  private final String projectId;

  public WebServerBase(List<String> args) throws IOException {
    if (args.size() < 2) {
      throw new RuntimeException("Not enough args for web server base");
    }

    int port = Integer.parseInt(args.remove(0));
    projectId = args.remove(1);
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    System.out.println("server started at " + port + " for project " + projectId);
    server.createContext("/", this);
    server.setExecutor(null);
    server.start();
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    Map<String, String> params = URLEncodedUtils.parse(exchange.getRequestURI(),
            StandardCharsets.UTF_8).stream()
        .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    String response = tryAction(params);
    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
    OutputStream os = exchange.getResponseBody();
    os.write(response.getBytes());
    os.close();
  }

  private String tryAction(Map<String, String> params) {
    try {
      triggerAction(params);
      return "success";
    } catch (Exception e) {
      return Common.stackTraceString(e);
    }
  }

  abstract void triggerAction(Map<String, String> params);
}
