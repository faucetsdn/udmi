package daq.pubber;

import static com.google.udmi.util.GeneralUtils.runtimeExec;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.FamilyDiscoveryEvent;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.LocalnetModel;
import udmi.schema.LocalnetState;

/**
 * Container class for dealing with the localnet subblock of UDMI.
 */
public class LocalnetManager {

  private static final List<Pattern> familyPatterns = ImmutableList.of(
      Pattern.compile(" +(inet) ([.\\d]+)/.+"),
      Pattern.compile(" +(inet6) ([:\\da-f]+)/.+"),
      Pattern.compile(" +link/(ether) ([:\\da-f]+) .+")
  );

  private static final Map<String, String> ifaceMap = ImmutableMap.of(
      "ether", "ether",
      "inet", "ipv4",
      "inet6", "ipv6"
  );

  private final Pubber parent;

  /**
   * Create a new container with the given Pubber parent.
   *
   * @param parent parent pubber class
   */
  public LocalnetManager(Pubber parent) {
    this.parent = parent;
    parent.deviceState.localnet = new LocalnetState();
    populateInterfaceAddresses();
  }

  /**
   * Parse the output of ip route/addr and turn it into a family addr map.
   *
   * <p>Start with default route with lowest metric, and then parse the interface addresses.
   *
   * <pre>
   * peringknife@peringknife-glaptop4:~/udmi$ ip route
   * default via 192.168.8.1 dev enp0s31f6 proto dhcp src 192.168.8.3 metric 100
   * default via 10.0.0.1 dev wlp0s20f3 proto dhcp src 10.0.0.142 metric 600
   * 10.0.0.0/24 dev wlp0s20f3 proto kernel scope link src 10.0.0.142 metric 600
   * 192.168.8.0/24 dev enp0s31f6 proto kernel scope link src 192.168.8.3 metric 100
   * 192.168.9.0/24 dev docker0 proto kernel scope link src 192.168.9.1 linkdown
   *
   * peringknife@peringknife-glaptop4:~/udmi$ ip addr show dev enp0s31f6
   * 2: enp0s31f6: *BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc .... 1000
   *     link/ether 8c:8c:aa:50:bc:72 brd ff:ff:ff:ff:ff:ff
   *     inet 192.168.8.3/24 brd 192.168.8.255 scope global dynamic noprefixroute enp0s31f6
   *        valid_lft 83596sec preferred_lft 83596sec
   *     inet6 fe80::11a9:496f:6596:b455/64 scope link noprefixroute
   *        valid_lft forever preferred_lft forever
   * </pre>
   */
  private void populateInterfaceAddresses() {
    parent.deviceState.localnet.families = new HashMap<>();
    String defaultInterface = getDefaultInterface();
    parent.info("Using addresses from default interface " + defaultInterface);
    Map<String, String> interfaceAddresses = getInterfaceAddresses(defaultInterface);
    interfaceAddresses.entrySet().forEach(this::addStateMapEntry);
    HashMap<String, FamilyLocalnetState> stateMap = new HashMap<>();
  }

  private void addStateMapEntry(Entry<String, String> entry) {
    FamilyLocalnetState stateEntry = new FamilyLocalnetState();
    stateEntry.addr = entry.getValue();
    String family = entry.getKey();
    parent.info("Family " + family + " address is " + stateEntry.addr);
    parent.deviceState.localnet.families.put(family, stateEntry);
  }

  private String getDefaultInterface() {
    List<String> routeLines = runtimeExec("ip", "route");
    AtomicReference<String> currentInterface = new AtomicReference<>();
    AtomicInteger currentMaxMetric = new AtomicInteger(Integer.MAX_VALUE);
    routeLines.forEach(line -> {
      String[] parts = line.split(" ", 12);
      if (parts[0].equals("default")) {
        int metric = Integer.parseInt(parts[10]);
        if (metric < currentMaxMetric.get()) {
          currentMaxMetric.set(metric);
          currentInterface.set(parts[4]);
        }
      }
    });
    return currentInterface.get();
  }

  private Map<String, String> getInterfaceAddresses(String defaultInterface) {
    List<String> strings = runtimeExec("ip", "addr", "show", "dev", defaultInterface);
    Map<String, String> interfaceMap = new HashMap<>();
    strings.forEach(line -> {
      for (Pattern pattern : familyPatterns) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
          interfaceMap.put(ifaceMap.get(matcher.group(1)), matcher.group(2));
        }
      }
    });
    return interfaceMap;
  }

  Map<String, FamilyDiscoveryEvent> enumerateFamilies() {
    return parent.deviceState.localnet.families.keySet().stream()
        .collect(toMap(key -> key, this::makeFamilyDiscoveryEvent));
  }

  private FamilyDiscoveryEvent makeFamilyDiscoveryEvent(String key) {
    FamilyDiscoveryEvent familyDiscoveryEvent = new FamilyDiscoveryEvent();
    familyDiscoveryEvent.addr = parent.deviceState.localnet.families.get(key).addr;
    return familyDiscoveryEvent;
  }
}
