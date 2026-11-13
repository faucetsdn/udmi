
package udmi.schema;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Scan Manifest
 * <p>
 * Metadata describing active discovery scan rounds executed during trace capture
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScanManifest {

    /**
     * Local gateway IPv4 address used during capture
     * 
     */
    @JsonProperty("gateway_ip")
    @JsonPropertyDescription("Local gateway IPv4 address used during capture")
    public String gateway_ip;
    /**
     * Network interface captured
     * 
     */
    @JsonProperty("interface")
    @JsonPropertyDescription("Network interface captured")
    public String _interface;
    /**
     * BPF packet filter applied during capture
     * 
     */
    @JsonProperty("filter")
    @JsonPropertyDescription("BPF packet filter applied during capture")
    public String filter;
    /**
     * RFC 3339 UTC timestamps marking active Who-Is discovery scan rounds
     * 
     */
    @JsonProperty("scan_round_timestamps")
    @JsonPropertyDescription("RFC 3339 UTC timestamps marking active Who-Is discovery scan rounds")
    public List<String> scan_round_timestamps;
    /**
     * Routed BACnet DNET numbers discovered via I-Am-Router-To-Network during trace
     * 
     */
    @JsonProperty("discovered_routed_networks")
    @JsonPropertyDescription("Routed BACnet DNET numbers discovered via I-Am-Router-To-Network during trace")
    public List<Integer> discovered_routed_networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.filter == null)? 0 :this.filter.hashCode()));
        result = ((result* 31)+((this.gateway_ip == null)? 0 :this.gateway_ip.hashCode()));
        result = ((result* 31)+((this.discovered_routed_networks == null)? 0 :this.discovered_routed_networks.hashCode()));
        result = ((result* 31)+((this._interface == null)? 0 :this._interface.hashCode()));
        result = ((result* 31)+((this.scan_round_timestamps == null)? 0 :this.scan_round_timestamps.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ScanManifest) == false) {
            return false;
        }
        ScanManifest rhs = ((ScanManifest) other);
        return ((((((this.filter == rhs.filter)||((this.filter!= null)&&this.filter.equals(rhs.filter)))&&((this.gateway_ip == rhs.gateway_ip)||((this.gateway_ip!= null)&&this.gateway_ip.equals(rhs.gateway_ip))))&&((this.discovered_routed_networks == rhs.discovered_routed_networks)||((this.discovered_routed_networks!= null)&&this.discovered_routed_networks.equals(rhs.discovered_routed_networks))))&&((this._interface == rhs._interface)||((this._interface!= null)&&this._interface.equals(rhs._interface))))&&((this.scan_round_timestamps == rhs.scan_round_timestamps)||((this.scan_round_timestamps!= null)&&this.scan_round_timestamps.equals(rhs.scan_round_timestamps))));
    }

}
