package com.google.bos.udmi.service.support;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import udmi.schema.IotAccess;
import udmi.schema.Level;

public class EtcdDataProvider implements IotDataProvider {

  public EtcdDataProvider(IotAccess config) {


  }

  @Override
  public void activate() {

  }

  @Override
  public void shutdown() {

  }

  @Override
  public void output(Level level, String message) {

  }

  private static void testEtcd() {
    System.err.println("Testing etcd");
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
      System.err.println("etcd reply: " + CSV_JOINER.join(strings));

      kvClient.delete(key).get();

    } catch (Exception e) {
      throw new RuntimeException("Exception testing etcd", e);
    }
  }


}
