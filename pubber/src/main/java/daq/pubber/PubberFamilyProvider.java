package daq.pubber;

import com.google.udmi.util.SiteModel;
import udmi.lib.intf.FamilyProvider;

/**
 * Simple interface for family provider as part of pubber.
 */
public interface PubberFamilyProvider extends FamilyProvider {

  void setSiteModel(SiteModel siteModel);
}
