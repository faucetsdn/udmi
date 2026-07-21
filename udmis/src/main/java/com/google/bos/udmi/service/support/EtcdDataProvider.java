package com.google.bos.udmi.service.support;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.bos.udmi.service.core.DistributorPipe.clientId;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.isNullOrNotEmpty;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.udmi.util.GeneralUtils;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.cluster.Member;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import udmi.schema.IotAccess;

/**
 * Data provider that uses etcd.
 */
public class EtcdDataProvider extends ContainerBase implements IotDataProvider {

  public static final GetOption PREFIXED_OPTION = GetOption.newBuilder().isPrefix(true).build();
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";
  private static final String CA_FILE_KEY = "ca_file";
  private static final String CERT_FILE_KEY = "cert_file";
  private static final String KEY_FILE_KEY = "key_file";
  private static final String RESULTING_PREFIX = "ip:///";
  private static final long QUERY_TIMEOUT_SEC = 10;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(QUERY_TIMEOUT_SEC);
  private static final String CLIENTS = "/clients/";
  private static final ByteSequence CONNECTED_KEY = bytes(CLIENTS + clientId);
  private static final int THRESHOLD_MIN = 10;
  private static final int HEARTBEAT_SEC = THRESHOLD_MIN * 60 / 4;
  private static final Duration CLIENT_THRESHOLD = Duration.ofMinutes(THRESHOLD_MIN);
  private static final GetOption LIST_OPT = GetOption.newBuilder().isPrefix(true).build();
  private final IotAccess config;
  private final Client client;
  private final KV kvClient;
  private final Lock lockClient;
  private final Map<String, String> options;
  private final boolean enabled;
  private ScheduledExecutorService scheduledExecutorService;

  /**
   * Create an instance of this component.
   */
  public EtcdDataProvider(IotAccess iotConfig) {
    options = parseOptions(iotConfig);
    enabled = isNullOrNotEmpty(options.get(ENABLED_KEY));
    config = iotConfig;
    client = enabled ? initializeClient() : null;
    kvClient = ifNotNullGet(client, Client::getKVClient);
    lockClient = ifNotNullGet(client, Client::getLockClient);
  }

  private static String asString(ByteSequence input) {
    return new String(input.getBytes());
  }

  private static ByteSequence bytes(String input) {
    return ByteSequence.from(input.getBytes());
  }

  @Override
  protected void periodicTask() {
    updateConnectedKey(client);
    reapConnectedKeys(client);
  }

  private void deleteEntry(String key) {
    try {
      kvClient.delete(bytes(key)).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("While deleting key " + key, e);
    }
  }

  private Map<String, String> getEntries(String keyPath) {
    try {
      GetResponse response =
          kvClient.get(bytes(keyPath), LIST_OPT).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      return response.getKvs().stream().collect(Collectors.toMap(
          kv -> asString(kv.getKey()).substring(keyPath.length()), kv -> asString(kv.getValue())));
    } catch (Exception e) {
      throw new RuntimeException("While listing db keys " + keyPath, e);
    }
  }

  private String getKey(String key) {
    try {
      GetResponse response = kvClient.get(bytes(key)).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      if (response.getCount() == 0) {
        return null;
      }
      if (response.getCount() > 1) {
        throw new IllegalStateException("Unexpected key return count " + response.getCount());
      }
      return asString(response.getKvs().get(0).getValue());
    } catch (Exception e) {
      throw new RuntimeException("While getting db entry " + key, e);
    }
  }

  private String cleanOption(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value;
  }

  private SslContext getSslContext() {
    String caFile = cleanOption(options.get(CA_FILE_KEY));
    String certFile = cleanOption(options.get(CERT_FILE_KEY));
    String keyFile = cleanOption(options.get(KEY_FILE_KEY));

    if (caFile == null && certFile == null && keyFile == null) {
      return null;
    }

    try {
      SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
      if (caFile != null) {
        sslContextBuilder.trustManager(new File(caFile));
      }
      if (certFile != null && keyFile != null) {
        sslContextBuilder.keyManager(new File(certFile), new File(keyFile));
      }
      return sslContextBuilder.build();
    } catch (Exception e) {
      throw new RuntimeException("While building SSL context for etcd", e);
    }
  }

  private Client initializeClient() {
    String target = variableSubstitution(config.project_id, "undefined project_id");
    boolean isSecure = target.startsWith(HTTPS_PREFIX);
    String expectedPrefix = isSecure ? HTTPS_PREFIX : HTTP_PREFIX;
    SslContext sslContext = getSslContext();

    ClientBuilder tmpClientBuilder = Client.builder().target(target);
    if (sslContext != null) {
      tmpClientBuilder.sslContext(sslContext);
    }

    try (Client tmpClient = tmpClientBuilder.build()) {
      info("Connecting to etcd target %s to glean client list", target);
      List<Member> members =
          tmpClient.getClusterClient().listMember().get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
              .getMembers();
      Set<String> uris = members.stream().flatMap(member -> member.getClientURIs().stream())
          .map(URI::toString).collect(Collectors.toSet());
      checkState(!uris.isEmpty(), "no member uris found");
      checkState(uris.stream().allMatch(uri -> uri.startsWith(expectedPrefix)),
          "inconsistent prefix");
      String collected = uris.stream().map(uri -> uri.substring(expectedPrefix.length()))
          .collect(Collectors.joining(","));
      String targets = RESULTING_PREFIX + collected;
      info("Gleaned etcd client targets %s", targets);
      ClientBuilder clientBuilder = Client.builder().target(targets)
          .connectTimeout(CONNECT_TIMEOUT);
      if (sslContext != null) {
        clientBuilder.sslContext(sslContext);
      }
      Client client = clientBuilder.build();
      updateConnectedKey(client);
      reapConnectedKeys(client);
      return client;
    } catch (Exception e) {
      error("Failed to connect to etcd at %s: %s", target, friendlyStackTrace(e));
      throw new RuntimeException("While connecting initial client " + target, e);
    }
  }

  private boolean isStaleKey(KeyValue kv, Instant threshold) {
    String value = asString(kv.getValue());
    try {
      return Instant.parse(value).isBefore(threshold);
    } catch (Exception e) {
      warn("Bad key value " + value + ", considering stale: " + friendlyStackTrace(e));
      return true;
    }
  }

  private AutoCloseable lockRef(String lockName) {
    try {
      long leaseId = client.getLeaseClient().grant(10).get().getID();
      ByteSequence lockKey = lockClient.lock(bytes(lockName), leaseId).get().getKey();
      info("Locked %s with lease %x", lockName, leaseId);
      return new LockCloser(lockName, leaseId, lockKey);
    } catch (Exception e) {
      throw new RuntimeException("While acquiring etcd lock", e);
    }
  }

  private void putKey(String key, String value) {
    try {
      kvClient.put(bytes(key), bytes(value)).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("While putting db entry " + key, e);
    }
  }

  private void reapConnectedKeys(Client client) {
    final Instant threshold = Instant.now().minus(CLIENT_THRESHOLD);
    try {
      KV kvClient = client.getKVClient();
      GetResponse response =
          kvClient.get(bytes(CLIENTS), PREFIXED_OPTION).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      debug("Reaping stale keys from pool of " + response.getKvs().size());
      List<String> staleKeys = response.getKvs().stream()
          .filter(kv -> isStaleKey(kv, threshold)).map(kv -> asString(kv.getKey())).toList();
      if (!staleKeys.isEmpty()) {
        debug("Deleting stale keys " + CSV_JOINER.join(staleKeys));
        staleKeys.forEach(key -> kvClient.delete(bytes(key)));
      }
    } catch (Exception e) {
      throw new RuntimeException("While reaping stale client keys", e);
    }
  }

  private void updateConnectedKey(Client client) {
    final String timestamp = GeneralUtils.getTimestamp();
    try {
      KV kvClient = client.getKVClient();
      GetResponse response = kvClient.get(CONNECTED_KEY).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      if (!response.getKvs().isEmpty()) {
        String result = asString(response.getKvs().get(0).getValue());
        debug("Retrieved previous client %s value %s", CONNECTED_KEY, result);
      }
      kvClient.put(CONNECTED_KEY, bytes(timestamp)).get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
      debug("Updated client %s with %s", CONNECTED_KEY, timestamp);
    } catch (Exception e) {
      throw new RuntimeException("While checking/updating client key " + CONNECTED_KEY, e);
    }
  }

  @Override
  public void activate() {
    super.activate();
    if (enabled) {
      // Ideally this would use the superclass scheduler, but that's not available b/c of legacy
      // configuration mechanisms... so just do this internally for now.
      scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
      scheduledExecutorService
          .scheduleAtFixedRate(this::periodicTask, HEARTBEAT_SEC, HEARTBEAT_SEC, TimeUnit.SECONDS);
    } else {
      info("Not enabled, not activating.");
    }
  }

  @Override
  public DataRef ref() {
    return new EtcdDataRef();
  }

  @Override
  public void shutdown() {
    try {
      if (enabled) {
        scheduledExecutorService.shutdown();
        scheduledExecutorService.awaitTermination(HEARTBEAT_SEC, TimeUnit.SECONDS);
        client.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("While shutting down", e);
    }
  }

  class EtcdDataRef extends DataRef {

    private static final String PATH_SEPARATOR = "/";
    private static final String KEY_SEPARATOR = ":";
    private static final String REGISTRY_PATH = PATH_SEPARATOR + "r" + PATH_SEPARATOR;
    private static final String DEVICE_PATH = PATH_SEPARATOR + "d" + PATH_SEPARATOR;
    private static final String COLLECT_PATH = PATH_SEPARATOR + "c" + PATH_SEPARATOR;

    private String getKeyPath(String key) {
      checkState(deviceId == null || registryId != null, "device without registry");
      return ifNotNullGet(registryId, id -> REGISTRY_PATH + id, "")
          + ifNotNullGet(deviceId, id -> DEVICE_PATH + id, "")
          + ifNotNullGet(collection, id -> COLLECT_PATH + id, "")
          + KEY_SEPARATOR + key;
    }

    @Override
    public void delete(String key) {
      deleteEntry(getKeyPath(key));
    }

    public Map<String, String> entries() {
      return getEntries(getKeyPath(""));
    }

    public String get(String key) {
      return getKey(getKeyPath(key));
    }

    @Override
    public AutoCloseable lock() {
      return lockRef(getKeyPath(""));
    }

    public void put(String key, String value) {
      putKey(getKeyPath(key), value);
    }

    @Override
    public void update(Map<String, String> puts, Set<String> deletes) {
      try {
        List<Op> ops = new ArrayList<>();
        if (puts != null) {
          puts.forEach((key, value) -> {
            if (value != null) {
              ops.add(Op.put(bytes(getKeyPath(key)), bytes(value), PutOption.DEFAULT));
            }
          });
        }
        if (deletes != null) {
          deletes.forEach(key -> {
            if (key != null) {
              ops.add(Op.delete(bytes(getKeyPath(key)), DeleteOption.DEFAULT));
            }
          });
        }
        if (!ops.isEmpty()) {
          kvClient.txn().Then(ops.toArray(new Op[0])).commit()
              .get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
      } catch (Exception e) {
        throw new RuntimeException("While executing batch update on " + getKeyPath(""), e);
      }
    }

    @Override
    public boolean updateIfMatch(String matchKey, String expectedValue, Map<String, String> puts,
        Set<String> deletes) {
      try {
        List<Op> ops = new ArrayList<>();
        if (puts != null) {
          puts.forEach((key, value) -> {
            if (value != null) {
              ops.add(Op.put(bytes(getKeyPath(key)), bytes(value), PutOption.DEFAULT));
            }
          });
        }
        if (deletes != null) {
          deletes.forEach(key -> {
            if (key != null) {
              ops.add(Op.delete(bytes(getKeyPath(key)), DeleteOption.DEFAULT));
            }
          });
        }
        if (!ops.isEmpty()) {
          Cmp cmp = expectedValue == null
              ? new Cmp(bytes(getKeyPath(matchKey)), Cmp.Op.EQUAL, CmpTarget.version(0))
              : new Cmp(bytes(getKeyPath(matchKey)), Cmp.Op.EQUAL,
                  CmpTarget.value(bytes(expectedValue)));

          TxnResponse response = kvClient.txn()
              .If(cmp)
              .Then(ops.toArray(new Op[0]))
              .commit()
              .get(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
          return response.isSucceeded();
        }
        return true;
      } catch (Exception e) {
        throw new RuntimeException(
            "While executing conditional batch update on " + getKeyPath(""), e);
      }
    }
  }

  private class LockCloser implements AutoCloseable {

    private final long leaseId;
    private final ByteSequence lockKey;
    private final String lockName;

    public LockCloser(String lockName, long leaseId, ByteSequence lockKey) {
      this.lockName = lockName;
      this.leaseId = leaseId;
      this.lockKey = lockKey;
    }

    @Override
    public void close() throws Exception {
      client.getLockClient().unlock(lockKey).get();
      client.getLeaseClient().revoke(leaseId).get();
      info("Released %s with lease %x", lockName, leaseId);
    }
  }
}

