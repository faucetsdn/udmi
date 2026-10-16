
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Upload
 * <p>
 * Sequential binary chunk upload for discovery artifacts (e.g. PCAP traces) framed by config.discovery and state.discovery.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryUpload {

    /**
     * RFC 3339 UTC timestamp the upload chunk was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the upload chunk was generated")
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
     * Protocol family associated with the discovery trace upload
     * 
     */
    @JsonProperty("family")
    @JsonPropertyDescription("Protocol family associated with the discovery trace upload")
    public String family;
    /**
     * The discovery scan trigger's generation timestamp
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("The discovery scan trigger's generation timestamp")
    public Date generation;
    /**
     * Unique session identifier for the chunked upload transmission
     * (Required)
     * 
     */
    @JsonProperty("session_id")
    @JsonPropertyDescription("Unique session identifier for the chunked upload transmission")
    public String session_id;
    /**
     * Sequence number for this upload chunk: 0 for start marker, 1..N for data chunks, -(N + 1) for terminal EOF marker
     * (Required)
     * 
     */
    @JsonProperty("event_no")
    @JsonPropertyDescription("Sequence number for this upload chunk: 0 for start marker, 1..N for data chunks, -(N + 1) for terminal EOF marker")
    public Integer event_no;
    /**
     *  0-based index of the transmitted chunk payload within the total session
     * 
     */
    @JsonProperty("chunk_index")
    @JsonPropertyDescription("0-based index of the transmitted chunk payload within the total session")
    public Integer chunk_index;
    /**
     * Total number of chunks comprising the complete transmission binary
     * 
     */
    @JsonProperty("total_chunks")
    @JsonPropertyDescription("Total number of chunks comprising the complete transmission binary")
    public Integer total_chunks;
    /**
     * SHA-256 hex digest of the complete unencoded binary payload (included on the terminal negative event_no marker)
     * 
     */
    @JsonProperty("sha256")
    @JsonPropertyDescription("SHA-256 hex digest of the complete unencoded binary payload (included on the terminal negative event_no marker)")
    public String sha256;
    /**
     * Base64-encoded binary chunk payload data
     * 
     */
    @JsonProperty("data")
    @JsonPropertyDescription("Base64-encoded binary chunk payload data")
    public String data;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.chunk_index == null)? 0 :this.chunk_index.hashCode()));
        result = ((result* 31)+((this.sha256 == null)? 0 :this.sha256 .hashCode()));
        result = ((result* 31)+((this.data == null)? 0 :this.data.hashCode()));
        result = ((result* 31)+((this.event_no == null)? 0 :this.event_no.hashCode()));
        result = ((result* 31)+((this.session_id == null)? 0 :this.session_id.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.total_chunks == null)? 0 :this.total_chunks.hashCode()));
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
        return (((((((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.chunk_index == rhs.chunk_index)||((this.chunk_index!= null)&&this.chunk_index.equals(rhs.chunk_index))))&&((this.sha256 == rhs.sha256)||((this.sha256 != null)&&this.sha256 .equals(rhs.sha256))))&&((this.data == rhs.data)||((this.data!= null)&&this.data.equals(rhs.data))))&&((this.event_no == rhs.event_no)||((this.event_no!= null)&&this.event_no.equals(rhs.event_no))))&&((this.session_id == rhs.session_id)||((this.session_id!= null)&&this.session_id.equals(rhs.session_id))))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.total_chunks == rhs.total_chunks)||((this.total_chunks!= null)&&this.total_chunks.equals(rhs.total_chunks))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
