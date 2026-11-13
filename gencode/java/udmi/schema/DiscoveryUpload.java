
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Upload
 * <p>
 * Sequential compressed capture chunk transport over MQTT upload/discovery framed by config.discovery and state.discovery.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryUpload {

    /**
     * RFC 3339 UTC timestamp the chunk was emitted
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the chunk was emitted")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Trigger timestamp / session generation ID matching config.discovery and state.discovery
     * (Required)
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Trigger timestamp / session generation ID matching config.discovery and state.discovery")
    public Date generation;
    /**
     * Protocol family identifier for the trace capture (e.g. bacnet, ether, ipv4)
     * (Required)
     * 
     */
    @JsonProperty("family")
    @JsonPropertyDescription("Protocol family identifier for the trace capture (e.g. bacnet, ether, ipv4)")
    public String family;
    /**
     *  0 for start marker, 1..N for gzip-compressed data chunks, and required -(N+1) for stream termination
     * (Required)
     * 
     */
    @JsonProperty("event_no")
    @JsonPropertyDescription("0 for start marker, 1..N for gzip-compressed data chunks, and required -(N+1) for stream termination")
    public Integer event_no;
    /**
     * Base64-encoded gzip-compressed binary PCAP chunk payload (present for event_no > 0)
     * 
     */
    @JsonProperty("data")
    @JsonPropertyDescription("Base64-encoded gzip-compressed binary PCAP chunk payload (present for event_no > 0)")
    public String data;
    /**
     * Expected SHA-256 hex digest of the complete unencoded PCAP file (required on terminal event_no < 0)
     * 
     */
    @JsonProperty("sha256")
    @JsonPropertyDescription("Expected SHA-256 hex digest of the complete unencoded PCAP file (required on terminal event_no < 0)")
    public String sha256;
    /**
     * Scan Manifest
     * <p>
     * Metadata describing active discovery scan rounds executed during trace capture
     * 
     */
    @JsonProperty("scan_manifest")
    @JsonPropertyDescription("Metadata describing active discovery scan rounds executed during trace capture")
    public ScanManifest scan_manifest;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.scan_manifest == null)? 0 :this.scan_manifest.hashCode()));
        result = ((result* 31)+((this.data == null)? 0 :this.data.hashCode()));
        result = ((result* 31)+((this.sha256 == null)? 0 :this.sha256 .hashCode()));
        result = ((result* 31)+((this.event_no == null)? 0 :this.event_no.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryUpload) == false) {
            return false;
        }
        DiscoveryUpload rhs = ((DiscoveryUpload) other);
        return (((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.scan_manifest == rhs.scan_manifest)||((this.scan_manifest!= null)&&this.scan_manifest.equals(rhs.scan_manifest))))&&((this.data == rhs.data)||((this.data!= null)&&this.data.equals(rhs.data))))&&((this.sha256 == rhs.sha256)||((this.sha256 != null)&&this.sha256 .equals(rhs.sha256))))&&((this.event_no == rhs.event_no)||((this.event_no!= null)&&this.event_no.equals(rhs.event_no))))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
