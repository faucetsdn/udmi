package daq.pubber;

import udmi.schema.PubberConfiguration;

public class VendorProvider extends ManagerBase implements LocalnetProvider {

  public VendorProvider(ManagerHost host, PubberConfiguration pubberConfiguration) {
    super(host, pubberConfiguration);
  }

  @Override
  public void startScan() {
  }
}
