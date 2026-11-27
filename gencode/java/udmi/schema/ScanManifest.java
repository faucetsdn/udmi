
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
    /**
     * Count of unique BACnet/IP devices observed via I-Am during trace
     * 
     */
    @JsonProperty("bacnet_ip_devices")
    @JsonPropertyDescription("Count of unique BACnet/IP devices observed via I-Am during trace")
    public Integer bacnet_ip_devices;
    /**
     * Count of unique routed MS/TP devices observed via I-Am during trace
     * 
     */
    @JsonProperty("routed_mstp_devices")
    @JsonPropertyDescription("Count of unique routed MS/TP devices observed via I-Am during trace")
    public Integer routed_mstp_devices;
    /**
     * BACnet device instance IDs observed from multiple distinct addresses during trace
     * 
     */
    @JsonProperty("duplicate_device_ids")
    @JsonPropertyDescription("BACnet device instance IDs observed from multiple distinct addresses during trace")
    public List<Integer> duplicate_device_ids;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.filter == null)? 0 :this.filter.hashCode()));
        result = ((result* 31)+((this.duplicate_device_ids == null)? 0 :this.duplicate_device_ids.hashCode()));
        result = ((result* 31)+((this.discovered_routed_networks == null)? 0 :this.discovered_routed_networks.hashCode()));
        result = ((result* 31)+((this.bacnet_ip_devices == null)? 0 :this.bacnet_ip_devices.hashCode()));
        result = ((result* 31)+((this.scan_round_timestamps == null)? 0 :this.scan_round_timestamps.hashCode()));
        result = ((result* 31)+((this.gateway_ip == null)? 0 :this.gateway_ip.hashCode()));
        result = ((result* 31)+((this.routed_mstp_devices == null)? 0 :this.routed_mstp_devices.hashCode()));
        result = ((result* 31)+((this._interface == null)? 0 :this._interface.hashCode()));
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
        return (((((((((this.filter == rhs.filter)||((this.filter!= null)&&this.filter.equals(rhs.filter)))&&((this.duplicate_device_ids == rhs.duplicate_device_ids)||((this.duplicate_device_ids!= null)&&this.duplicate_device_ids.equals(rhs.duplicate_device_ids))))&&((this.discovered_routed_networks == rhs.discovered_routed_networks)||((this.discovered_routed_networks!= null)&&this.discovered_routed_networks.equals(rhs.discovered_routed_networks))))&&((this.bacnet_ip_devices == rhs.bacnet_ip_devices)||((this.bacnet_ip_devices!= null)&&this.bacnet_ip_devices.equals(rhs.bacnet_ip_devices))))&&((this.scan_round_timestamps == rhs.scan_round_timestamps)||((this.scan_round_timestamps!= null)&&this.scan_round_timestamps.equals(rhs.scan_round_timestamps))))&&((this.gateway_ip == rhs.gateway_ip)||((this.gateway_ip!= null)&&this.gateway_ip.equals(rhs.gateway_ip))))&&((this.routed_mstp_devices == rhs.routed_mstp_devices)||((this.routed_mstp_devices!= null)&&this.routed_mstp_devices.equals(rhs.routed_mstp_devices))))&&((this._interface == rhs._interface)||((this._interface!= null)&&this._interface.equals(rhs._interface))));
    }

}
