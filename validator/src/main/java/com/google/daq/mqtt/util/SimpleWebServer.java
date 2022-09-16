package com.google.daq.mqtt.util;

import com.google.api.client.util.ArrayMap;
import com.google.common.base.Joiner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

/**
 * Simple web server used as an endpoint for a cron scheduling job.
 */
public class SimpleWebServer {

  private final HttpServer server;
  private final Map<String, Consumer<Map<String, String>>> mockHandlers;

  protected SimpleWebServer(List<String> args) {
    try {
      if (args.size() < 1) {
        throw new RuntimeException("Need server port argument");
      }
      int port = Integer.parseInt(args.remove(0));
      if (port > 0) {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        System.err.println("server started on port " + port);
        server.start();
        mockHandlers = null;
      } else {
        System.err.println("skipping server port 0");
        server = null;
        mockHandlers = new HashMap<>();
      }
    } catch (Exception e) {
      throw new RuntimeException("While creating web server: " + Joiner.on(" ").join(args), e);
    }
  }

  protected void setHandler(String keyword, Consumer<Map<String, String>> handler) {
    if (server == null) {
      mockHandlers.put(keyword, handler);
    } else {
      server.createContext("/" + keyword, exchange -> {
        Map<String, String> params = URLEncodedUtils.parse(exchange.getRequestURI(),
                StandardCharsets.UTF_8).stream()
            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
        String response = tryAction(handler, params);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
      });
    }
  }

  public String tryHandler(String handler, Map<String, String> params) {
    return tryAction(mockHandlers.get(handler), params);
  }

  private String tryAction(Consumer<Map<String, String>> handler, Map<String, String> params) {
    try {
      System.err.println("Handling request " + params);
      handler.accept(params);
      return "success";
    } catch (Throwable e) {
      return Common.stackTraceString(e);
    }
  }
}
