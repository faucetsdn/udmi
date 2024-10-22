package udmi.lib.impl;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import udmi.lib.ProtocolFamily;
import udmi.lib.impl.IpProvider;

/**
 * Basic interface parsing tests for pubber.
 */
public class IpManagerTest {

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

  private static final List<String> INTERFACE_STRINGS_DEBIAN = ImmutableList.of(
      " 2: enp0s31f6: *BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc .... 1000",
      "     link/ether 8c:8c:aa:50:bc:72 brd ff:ff:ff:ff:ff:ff",
      "     inet 192.168.8.3/24 brd 192.168.8.255 scope global dynamic noprefixroute enp0s31f6",
      "        valid_lft 83596sec preferred_lft 83596sec",
      "     inet6 fe80::11a9:496f:6596:b455/64 scope link noprefixroute",
      "        valid_lft forever preferred_lft forever"
  );

  // For some reason, some (unknown) systems add a leading "none" to the list :-/.
  private static final List<String> IP_ROUTE_ALTERNATE = ImmutableList.of(
      "none default via 192.168.1.1 dev ethX proto unspec metric 256",
      "none 192.168.0.0/23 dev eth0 proto unspec metric 256",
      "none 192.168.1.141 dev eth0 proto unspec metric 256",
      "none 192.168.1.255 dev eth0 proto unspec metric 256"
  );

  private static final List<String> IP_ADDR_ALTERNATE = ImmutableList.of(
      "20: eth0: <> mtu 1500 group default qlen 1",
      "    link/ether 38:14:28:0c:bb:b2",
      "    inet 192.168.1.141/23 brd 192.168.1.255 scope global dynamic",
      "       valid_lft forever preferred_lft forever",
      "    inet6 fe80::c452:1b85:69eb:bc7b/64 scope link dynamic",
      "       valid_lft forever preferred_lft forever"
  );

  @Test
  public void testGetDefaultInterfaceOne() {
    String defaultInterface = IpProvider.getDefaultInterfaceStatic(IP_ROUTE_BASIC);
    assertEquals("default interface", "eth0", defaultInterface);
  }

  @Test
  public void testGetDefaultInterfaceTwo() {
    String defaultInterface = IpProvider.getDefaultInterfaceStatic(IP_ROUTE_DUAL);
    assertEquals("default interface", "enp0s31f6", defaultInterface);
  }

  @Test
  public void testGetInterfaceAddresses() {
    Map<String, String> interfaceAddresses = IpProvider.getInterfaceAddressesStatic(
        INTERFACE_STRINGS_DEBIAN);
    ImmutableMap<String, String> expectedInterfaces = ImmutableMap.of(
        ProtocolFamily.ETHER, "8c:8c:aa:50:bc:72",
        ProtocolFamily.IPV_4, "192.168.8.3",
        ProtocolFamily.IPV_6, "fe80::11a9:496f:6596:b455"
    );
    assertEquals("Extracted interface addresses", expectedInterfaces, interfaceAddresses);
  }

  @Test
  public void testAlternateInputs() {
    String defaultInterface = IpProvider.getDefaultInterfaceStatic(IP_ROUTE_ALTERNATE);
    assertEquals("expected alternate default interface", "ethX", defaultInterface);
    Map<String, String> interfaceAddresses = IpProvider.getInterfaceAddressesStatic(
        IP_ADDR_ALTERNATE);
    ImmutableMap<String, String> expectedInterfaces = ImmutableMap.of(
        ProtocolFamily.IPV_4, "192.168.1.141",
        ProtocolFamily.IPV_6, "fe80::c452:1b85:69eb:bc7b"
    );
    assertEquals("Extracted alternate interface addresses", expectedInterfaces, interfaceAddresses);
  }
}