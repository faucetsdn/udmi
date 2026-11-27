package udmi.lib.client.manager;

/**
 * Identifies one independently scheduled discovery scan: either a {@code discovery.families}
 * entry (keyed by family) or a named {@code discovery.scans} entry (keyed by scan name).
 */
public record DiscoveryScan(boolean named, String name) {

  public static DiscoveryScan forFamily(String family) {
    return new DiscoveryScan(false, family);
  }

  public static DiscoveryScan forScan(String scanName) {
    return new DiscoveryScan(true, scanName);
  }

  @Override
  public String toString() {
    return (named ? "scans." : "families.") + name;
  }
}
