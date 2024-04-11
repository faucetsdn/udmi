package com.google.bos.udmi.service.support;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.bytes;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.core.DistributorPipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.udmi.util.GeneralUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.cluster.Member;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import udmi.schema.IotAccess;

/**
 * Data provider that uses etcd.
 */
public class EtcdDataProvider extends ContainerBase implements IotDataProvider {

  private static final String EXPECTED_PREFIX = "http://";
  private static final String RESULTING_PREFIX = "ip:///";
  private static final long QUERY_TIMEOUT_SEC = 10;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(QUERY_TIMEOUT_SEC);
  private static final ByteSequence CONNECTED_KEY =
      bytes(format("/clients/%s", DistributorPipe.clientId));
  private final IotAccess config;
  private final Client client;

  public EtcdDataProvider(IotAccess iotConfig) {
    config = iotConfig;
    client = initializeClient();
  }

  private Client initializeClient() {
    String target = requireNonNull(config.project_id, "undefined project_id");
    try (Client tmpClient = Client.builder().target(target).build()) {
      debug("Connecting to target %s to glean client list", target);
      List<Member> members =
          tmpClient.getClusterClient().listMember().get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
              .getMembers();
      Set<String> uris = members.stream().flatMap(member -> member.getClientURIs().stream())
          .map(URI::toString).collect(Collectors.toSet());
      checkState(!uris.isEmpty(), "no member uris found");
      checkState(uris.stream().allMatch(uri -> uri.startsWith(EXPECTED_PREFIX)),
          "inconsistent prefix");
      String collected = uris.stream().map(uri -> uri.substring(EXPECTED_PREFIX.length()))
          .collect(Collectors.joining(","));
      String targets = RESULTING_PREFIX + collected;
      debug("Gleaned client targets " + targets);
      Client client = Client.builder().target(targets).connectTimeout(CONNECT_TIMEOUT).build();
      String timestamp = GeneralUtils.getTimestamp();
      KV kvClient = client.getKVClient();
      kvClient.put(CONNECTED_KEY, bytes(timestamp)).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      String result = new String(
          kvClient.get(CONNECTED_KEY).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS).getKvs().get(0)
              .getValue().getBytes());
      debug("Retrieved value %s", result);
      debug("Updated client %s with %s", CONNECTED_KEY, timestamp);
      return client;
    } catch (Exception e) {
      throw new RuntimeException("While connecting initial client " + target, e);
    }
  }

  @Override
  public void activate() {
    super.activate();
  }

  @Override
  public void shutdown() {
    ifNotNullThen(client, client::close);
  }


}
