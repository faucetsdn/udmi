package com.google.daq.mqtt.registrar;

import com.google.common.base.Preconditions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

public class WebServer {

  public static final String SITE_PREFIX = "sites/";
  public static final String SCAN_LIMIT = "10m";

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      throw new RuntimeException("Usage: swarm listen_port target_project");
    }

    int port = Integer.parseInt(args[0]);
    String projectId = args[1];
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    System.out.println("server started at " + port + " for project " + projectId);
    server.createContext("/", new RootHandler(projectId));
    server.setExecutor(null);
    server.start();
  }

  public static class RootHandler implements HttpHandler {

    private final String projectId;

    public RootHandler(String projectId) {
      this.projectId = projectId;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      Map<String, String> params = URLEncodedUtils.parse(exchange.getRequestURI(),
              StandardCharsets.UTF_8).stream()
          .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
      String response = triggerSwarm(params);
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes());
      os.close();
    }

    private void addArgs(List<String> args, String a, String b) {
      args.add(Preconditions.checkNotNull(a, "param key"));
      args.add(Preconditions.checkNotNull(b, "param value"));
    }

    private String triggerSwarm(Map<String, String> params) {
      try {
        String siteName = SITE_PREFIX + params.get("site");
        ArrayList<String> args = new ArrayList<>();
        addArgs(args, "-l", SCAN_LIMIT);
        addArgs(args, "-s", siteName);
        addArgs(args, "-p", projectId);
        addArgs(args, "-f", params.get("topic"));
        Registrar.main(args.toArray(new String[0]));
        return "success";
      } catch (Exception e) {
        return "error " + e;
      }
    }
  }
}
