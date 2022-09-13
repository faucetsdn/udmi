package com.google.daq.mqtt.util;

import com.google.common.base.Joiner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

/**
 * Simple web server used as an endpoint for a cron scheduling job.
 */
public class SimpleWebServer implements HttpHandler {

  private Consumer<Map<String, String>> handler;

  protected SimpleWebServer(List<String> args) {
    try {
      if (args.size() < 1) {
        throw new RuntimeException("Need server port argument");
      }
      int port = Integer.parseInt(args.remove(0));
      HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
      System.out.println("server started on port " + port);
      server.createContext("/", this);
      server.start();
    } catch (Exception e) {
      throw new RuntimeException("While creating web server: " + Joiner.on(" ").join(args), e);
    }
  }

  public void setHandler(Consumer<Map<String, String>> handler) {
    this.handler = handler;
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
      System.err.println("Handling request " + params);
      handler.accept(params);
      return "success";
    } catch (Throwable e) {
      return Common.stackTraceString(e);
    }
  }
}
