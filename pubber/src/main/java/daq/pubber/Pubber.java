package daq.pubber;

import daq.pubber.impl.host.PubberPublisherHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Class for running UDMI publisher.
 */
public class Pubber {

  static final Logger LOG = LoggerFactory.getLogger(Pubber.class);
  static final String USAGE = "Usage: config_file or { project_id site_path/ device_id serial_no }";

  /**
   * Start UDMI publisher with command line args.
   */
  public static void main(String[] args) {
    try {
      PubberPublisherHost publisher = null;
      try {
        if (args.length == 1) {
          publisher = new PubberPublisherHost(args[0]);
        } else if (args.length == 4) {
          publisher = new PubberPublisherHost(args[0], args[1], args[2], args[3]);
        } else {
          throw new IllegalArgumentException(USAGE);
        }
        publisher.initialize();
        publisher.startConnection();
      } catch (Exception e) {
        if (publisher != null) {
          publisher.shutdown();
        }
        throw new RuntimeException("While starting main", e);
      }
      LOG.info("Done with main");
    } catch (Exception e) {
      LOG.error("Exception starting main", e);
      System.exit(-1);
    }
  }
}
