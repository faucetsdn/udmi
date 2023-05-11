package daq.pubber;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Basic interface parsing tests for pubber.
 */
public class LocalnetManagerTest {

  private static final List<String> INTERFACE_STRINGS_DEBIAN = ImmutableList.of(
      " 2: enp0s31f6: *BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc .... 1000",
      "     link/ether 8c:8c:aa:50:bc:72 brd ff:ff:ff:ff:ff:ff",
      "     inet 192.168.8.3/24 brd 192.168.8.255 scope global dynamic noprefixroute enp0s31f6",
      "        valid_lft 83596sec preferred_lft 83596sec",
      "     inet6 fe80::11a9:496f:6596:b455/64 scope link noprefixroute",
      "        valid_lft forever preferred_lft forever"
  );
  private static final List<String> IP_ROUTE_BASIC = ImmutableList.of(
      "default via 172.17.0.1 dev eth0",
      "172.17.0.0/16 dev eth0 proto kernel scope link src 172.17.0.4",
      "172.18.0.0/16 dev docker0 proto kernel scope link src 172.18.0.1 linkdown"
  );
  private static final List<String> IP_ROUTE_DUAL = ImmutableList.of(
      "default via 192.168.8.1 dev enp0s31f6 proto dhcp src 192.168.8.3 metric 100",
      "default via 10.0.0.1 dev wlp0s20f3 proto dhcp src 10.0.0.142 metric 600",
      "10.0.0.0/24 dev wlp0s20f3 proto kernel scope link src 10.0.0.142 metric 600",
      "192.168.8.0/24 dev enp0s31f6 proto kernel scope link src 192.168.8.3 metric 100",
      "192.168.9.0/24 dev docker0 proto kernel scope link src 192.168.9.1 linkdown"
  );

  @Test
  public void testGetDefaultInterfaceOne() {
    String defaultInterface = LocalnetManager.getDefaultInterface(IP_ROUTE_BASIC);
    assertEquals("default interface", "eth0", defaultInterface);
  }

  @Test
  public void testGetDefaultInterfaceTwo() {
    String defaultInterface = LocalnetManager.getDefaultInterface(IP_ROUTE_DUAL);
    assertEquals("default interface", "enp0s31f6", defaultInterface);
  }

  @Test
  public void testGetInterfaceAddresses() {
    Map<String, String> interfaceAddresses = LocalnetManager.getInterfaceAddresses(
        INTERFACE_STRINGS_DEBIAN);
    ImmutableMap<String, String> expectedInterfaces = ImmutableMap.of(
        "ether", "8c:8c:aa:50:bc:72",
        "ipv4", "192.168.8.3",
        "ipv6", "fe80::11a9:496f:6596:b455"
    );
    assertEquals("Extracted interface addresses", expectedInterfaces, interfaceAddresses);
  }
}