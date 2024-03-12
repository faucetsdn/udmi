package daq.pubber;

import static java.lang.Math.floor;
import static java.lang.Math.random;
import static java.lang.String.format;
import static udmi.schema.Common.ProtocolFamily.VENDOR;

import udmi.schema.Common.ProtocolFamily;
import udmi.schema.FamilyLocalnetState;
import udmi.schema.PubberConfiguration;

public class VendorProvider extends ManagerBase implements LocalnetProvider {

  public static final String RANDOM_ID = format("%08x", (long) floor(random() * 0x100000000L));
  private final LocalnetManager localnetHost;

  public VendorProvider(ManagerHost host, ProtocolFamily family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
    localnetHost = (LocalnetManager) host;
    updateStateAddress();
  }

  private void updateStateAddress() {
    FamilyLocalnetState stateEntry = new FamilyLocalnetState();
    stateEntry.addr = RANDOM_ID;
    localnetHost.update(VENDOR, stateEntry);
  }
}
