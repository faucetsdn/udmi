package com.google.bos.udmi.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.udmi.util.JsonUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EtcdExplorerServerTest {

  private EtcdDataProvider mockEtcdProvider;
  private EtcdExplorerServer server;
  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    mockEtcdProvider = mock(EtcdDataProvider.class);
    server = new EtcdExplorerServer(0, mockEtcdProvider);
    server.start();
    baseUrl = "http://localhost:" + server.getPort();
    httpClient = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void testGetRegistries() throws Exception {
    when(mockEtcdProvider.getPrefixKeys("/r/")).thenReturn(List.of(
        "/r/cloud_iot_registry/d/AHU-1",
        "/r/cloud_iot_registry/d/AHU-2",
        "/r/acme_corp/d/dev-1"
    ));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/registries"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    Map<String, Object> body = JsonUtil.asMap(response.body());
    assertNotNull(body.get("registries"));
    @SuppressWarnings("unchecked")
    List<String> registries = (List<String>) body.get("registries");
    assertEquals(2, registries.size());
    assertEquals(3, ((Number) body.get("totalDevicesCount")).intValue());
    assertTrue(registries.contains("acme_corp"));
    assertTrue(registries.contains("cloud_iot_registry"));
    verify(mockEtcdProvider).getPrefixKeys("/r/");
  }

  @Test
  void testGetDevices() throws Exception {
    when(mockEtcdProvider.getPrefixKeys("/r/cloud_iot_registry/d/")).thenReturn(List.of(
        "/r/cloud_iot_registry/d/AHU-1:numId",
        "/r/cloud_iot_registry/d/AHU-1/c/state:latest",
        "/r/cloud_iot_registry/d/CHILLER-102:numId"
    ));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/registries/cloud_iot_registry/devices"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    Map<String, Object> body = JsonUtil.asMap(response.body());
    assertEquals("cloud_iot_registry", body.get("registryId"));
    @SuppressWarnings("unchecked")
    List<String> devices = (List<String>) body.get("devices");
    assertEquals(2, devices.size());
    assertTrue(devices.contains("AHU-1"));
    assertTrue(devices.contains("CHILLER-102"));
    verify(mockEtcdProvider).getPrefixKeys("/r/cloud_iot_registry/d/");
  }

  @Test
  void testGetProperties() throws Exception {
    when(mockEtcdProvider.getPrefixEntries("/r/cloud_iot_registry/d/AHU-1")).thenReturn(Map.of(
        "/r/cloud_iot_registry/d/AHU-1:numId", "12345",
        "/r/cloud_iot_registry/d/AHU-1/c/state:latest", "{\"ver\":1}"
    ));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/registries/cloud_iot_registry/devices/AHU-1/properties"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    Map<String, Object> body = JsonUtil.asMap(response.body());
    assertEquals("cloud_iot_registry", body.get("registryId"));
    assertEquals("AHU-1", body.get("deviceId"));
    @SuppressWarnings("unchecked")
    Map<String, String> properties = (Map<String, String>) body.get("properties");
    assertNotNull(properties);
    assertEquals("12345", properties.get(":numId"));
    assertEquals("{\"ver\":1}", properties.get("/c/state:latest"));
    verify(mockEtcdProvider).getPrefixEntries("/r/cloud_iot_registry/d/AHU-1");
  }

  @Test
  void testServeStaticHtml() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/index.html"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("UDMI ETCD Explorer"));
  }

  @Test
  void testNotFoundEndpoint() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/registries/foo/bar/baz"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(404, response.statusCode());
  }
}
