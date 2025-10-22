package com.google.udmi.util;

import java.io.File;
import udmi.schema.Metadata;

/**
 * Interface for handling a device as part of a site model.
 */
public interface SiteDevice {

  Metadata getMetadata();

  File getOutDir();
}
