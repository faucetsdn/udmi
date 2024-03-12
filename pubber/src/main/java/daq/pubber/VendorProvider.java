package daq.pubber;

import udmi.schema.Common.ProtocolFamily;
import udmi.schema.PubberConfiguration;

public class VendorProvider extends ManagerBase implements LocalnetProvider {

  public VendorProvider(ManagerHost host, ProtocolFamily family,
      PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
  }

  @Override
  public void startScan() {
  }
}
