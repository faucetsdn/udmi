package com.google.bos.udmi.service.support;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.core.DistributorPipe;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.udmi.util.GeneralUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.cluster.Member;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.IotAccess;

/**
 * Data provider that uses etcd.
 */
public class EtcdDataProvider extends ContainerBase implements IotDataProvider {

  private static final long QUERY_TIMEOUT_SEC = 10;
  private static final ByteSequence CONNECTED_KEY =
      getByteSequence(format("clients/%s", DistributorPipe.clientId));
  private final IotAccess config;
  private final Client client;

  public EtcdDataProvider(IotAccess iotConfig) {
    config = iotConfig;
    client = initializeClient();
  }

  @NotNull
  private static ByteSequence getByteSequence(String input) {
    return ByteSequence.from(input.getBytes());
  }

  private Client initializeClient() {
    String target = format("ip://%s", requireNonNull(config.project_id, "undefined project_id"));
    try (Client tmpClient = Client.builder().target(target).build()) {
      debug("Connecting to target %s to glean client list", target);
      List<Member> members =
          tmpClient.getClusterClient().listMember().get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
              .getMembers();
      Set<URI> uris = members.stream().flatMap(member -> member.getClientURIs().stream())
          .collect(Collectors.toSet());
      String targets = uris.stream().map(URI::toString).collect(Collectors.joining(","));
      debug("Gleaned client targets " + targets);
      Client client = Client.builder().target(targets).build();
      String timestamp = GeneralUtils.getTimestamp();
      client.getKVClient().put(CONNECTED_KEY, getByteSequence(timestamp))
          .get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      debug("Updated client %s at %s", CONNECTED_KEY, timestamp);
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
