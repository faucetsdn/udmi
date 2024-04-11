package com.google.bos.udmi.service.support;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;

import com.google.bos.udmi.service.pod.ContainerBase;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import udmi.schema.IotAccess;

public class EtcdDataProvider extends ContainerBase implements IotDataProvider {

  private final IotAccess config;

  public EtcdDataProvider(IotAccess config) {
    this.config = config;
  }

  @Override
  public void activate() {
    super.activate();
    warn("Testing cluster etcd");
    String etcdTarget =
        "ip:///etcd-set-0.etcd-set:2379,etcd-set-1.etcd-set:2379,etcd-set-2.etcd:2379";
    try (Client client = Client.builder().target(etcdTarget).build()) {
      KV kvClient = client.getKVClient();
      ByteSequence key = ByteSequence.from("test_key".getBytes());
      ByteSequence value = ByteSequence.from("test_value".getBytes());

      kvClient.put(key, value).get();

      CompletableFuture<GetResponse> getFuture = kvClient.get(key);

      GetResponse response = getFuture.get();
      List<String> strings = response.getKvs().stream().map(KeyValue::getValue)
          .map(bytes -> new String(bytes.getBytes())).toList();
      info("etcd reply: " + CSV_JOINER.join(strings));

      kvClient.delete(key).get();

    } catch (Exception e) {
      error("Exception testing etcd: %s", friendlyStackTrace(e));
    }
  }


}
