
package udmi.schema;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Discovery Config
 * <p>
 * Configuration for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FamilyDiscoveryConfig {

    /**
     * Generational marker for controlling discovery
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling discovery")
    public Date generation;
    /**
     * Period, in seconds, for automatic scanning
     * 
     */
    @JsonProperty("scan_interval_sec")
    @JsonPropertyDescription("Period, in seconds, for automatic scanning")
    public Integer scan_interval_sec;
    /**
     * Scan duration, in seconds
     * 
     */
    @JsonProperty("scan_duration_sec")
    @JsonPropertyDescription("Scan duration, in seconds")
    public Integer scan_duration_sec;
    @JsonProperty("addrs")
    public List<String> addrs;
    @JsonProperty("networks")
    public List<String> networks;
    /**
     * Holdoff time for passively discovered devices
     * 
     */
    @JsonProperty("passive_sec")
    @JsonPropertyDescription("Holdoff time for passively discovered devices")
    public Integer passive_sec;
    @JsonProperty("depth")
    public udmi.schema.Enumerations.Depth depth;
    /**
     * Network interface to target for trace capture operations
     * 
     */
    @JsonProperty("interface")
    @JsonPropertyDescription("Network interface to target for trace capture operations")
    public String _interface;
    /**
     * Protocol or packet filter expression (e.g. BPF filter)
     * 
     */
    @JsonProperty("filter")
    @JsonPropertyDescription("Protocol or packet filter expression (e.g. BPF filter)")
    public String filter;
    /**
     * Maximum byte volume cap for trace collection to prevent exhaustion
     * 
     */
    @JsonProperty("max_bytes")
    @JsonPropertyDescription("Maximum byte volume cap for trace collection to prevent exhaustion")
    public Integer max_bytes;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.scan_interval_sec == null)? 0 :this.scan_interval_sec.hashCode()));
        result = ((result* 31)+((this.filter == null)? 0 :this.filter.hashCode()));
        result = ((result* 31)+((this.depth == null)? 0 :this.depth.hashCode()));
        result = ((result* 31)+((this.passive_sec == null)? 0 :this.passive_sec.hashCode()));
        result = ((result* 31)+((this.scan_duration_sec == null)? 0 :this.scan_duration_sec.hashCode()));
        result = ((result* 31)+((this.addrs == null)? 0 :this.addrs.hashCode()));
        result = ((result* 31)+((this.max_bytes == null)? 0 :this.max_bytes.hashCode()));
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        result = ((result* 31)+((this._interface == null)? 0 :this._interface.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyDiscoveryConfig) == false) {
            return false;
        }
        FamilyDiscoveryConfig rhs = ((FamilyDiscoveryConfig) other);
        return (((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.scan_interval_sec == rhs.scan_interval_sec)||((this.scan_interval_sec!= null)&&this.scan_interval_sec.equals(rhs.scan_interval_sec))))&&((this.filter == rhs.filter)||((this.filter!= null)&&this.filter.equals(rhs.filter))))&&((this.depth == rhs.depth)||((this.depth!= null)&&this.depth.equals(rhs.depth))))&&((this.passive_sec == rhs.passive_sec)||((this.passive_sec!= null)&&this.passive_sec.equals(rhs.passive_sec))))&&((this.scan_duration_sec == rhs.scan_duration_sec)||((this.scan_duration_sec!= null)&&this.scan_duration_sec.equals(rhs.scan_duration_sec))))&&((this.addrs == rhs.addrs)||((this.addrs!= null)&&this.addrs.equals(rhs.addrs))))&&((this.max_bytes == rhs.max_bytes)||((this.max_bytes!= null)&&this.max_bytes.equals(rhs.max_bytes))))&&((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks))))&&((this._interface == rhs._interface)||((this._interface!= null)&&this._interface.equals(rhs._interface))));
    }

}
