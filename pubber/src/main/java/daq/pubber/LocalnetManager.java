package daq.pubber;

import java.util.HashMap;
import udmi.schema.Config;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetState;
import udmi.schema.State;

public class LocalnetManager {

  public LocalnetManager(State deviceState, Config deviceConfig) {
    deviceState.localnet = new LocalnetState();
    deviceState.localnet.families = enumerateInterfaceAddresses();
  }

  /**
   * Parse the output of ip route/addr and turn it into a family addr map.
   *
   * Start with default route with lowest metric.
   *
   * peringknife@peringknife-glaptop4:~/udmi$ ip route
   * default via 192.168.8.1 dev enp0s31f6 proto dhcp src 192.168.8.3 metric 100
   * default via 10.0.0.1 dev wlp0s20f3 proto dhcp src 10.0.0.142 metric 600
   * 10.0.0.0/24 dev wlp0s20f3 proto kernel scope link src 10.0.0.142 metric 600
   * 192.168.8.0/24 dev enp0s31f6 proto kernel scope link src 192.168.8.3 metric 100
   * 192.168.9.0/24 dev docker0 proto kernel scope link src 192.168.9.1 linkdown
   *
   * And then parse the addresses for that interface.
   *
   * peringknife@peringknife-glaptop4:~/udmi$ ip addr show dev enp0s31f6
   * 2: enp0s31f6: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UP group default qlen 1000
   *     link/ether 8c:8c:aa:50:bc:72 brd ff:ff:ff:ff:ff:ff
   *     inet 192.168.8.3/24 brd 192.168.8.255 scope global dynamic noprefixroute enp0s31f6
   *        valid_lft 83596sec preferred_lft 83596sec
   *     inet6 fe80::11a9:496f:6596:b455/64 scope link noprefixroute
   *        valid_lft forever preferred_lft forever
   */
  private HashMap<String, FamilyLocalnetState> enumerateInterfaceAddresses() {

  }
}
