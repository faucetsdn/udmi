
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Trace Discovery Config
 * <p>
 * Configuration parameters for trace-level discovery operations (when depth is 'trace')
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceDiscoveryConfig {

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
        result = ((result* 31)+((this._interface == null)? 0 :this._interface.hashCode()));
        result = ((result* 31)+((this.filter == null)? 0 :this.filter.hashCode()));
        result = ((result* 31)+((this.max_bytes == null)? 0 :this.max_bytes.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TraceDiscoveryConfig) == false) {
            return false;
        }
        TraceDiscoveryConfig rhs = ((TraceDiscoveryConfig) other);
        return ((((this._interface == rhs._interface)||((this._interface!= null)&&this._interface.equals(rhs._interface)))&&((this.filter == rhs.filter)||((this.filter!= null)&&this.filter.equals(rhs.filter))))&&((this.max_bytes == rhs.max_bytes)||((this.max_bytes!= null)&&this.max_bytes.equals(rhs.max_bytes))));
    }

}
