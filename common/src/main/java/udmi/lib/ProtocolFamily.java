package udmi.lib;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public interface ProtocolFamily {

  String VENDOR = "vendor";
  String IPV_4 = "ipv4";
  String IPV_6 = "ipv6";
  String ETHER = "ether";
  String IOT = "iot";
  String BACNET = "bacnet";
  String MODBUS = "modbus";
  String INVALID = "invalid";
  String FQDN = "fqdn";

  Set<String> FAMILIES = ImmutableSet.of(VENDOR, IPV_4, IPV_6, ETHER, IOT, BACNET, MODBUS, FQDN);
}
