
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Stream Events
 * <p>
 * Sequential data stream chunks for reliable transport over MQTT (e.g. PCAP traces, firmware blobs, reliable alarms, logs playback).
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamEvents {

    /**
     * RFC 3339 UTC timestamp the stream chunk event was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the stream chunk event was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Unique session identifier for the continuous stream transmission
     * 
     */
    @JsonProperty("session_id")
    @JsonPropertyDescription("Unique session identifier for the continuous stream transmission")
    public String session_id;
    /**
     * Sequence number for this stream event to support reliable sequential delivery and reassembly
     * 
     */
    @JsonProperty("event_no")
    @JsonPropertyDescription("Sequence number for this stream event to support reliable sequential delivery and reassembly")
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
     * Base64-encoded binary chunk payload data
     * 
     */
    @JsonProperty("data")
    @JsonPropertyDescription("Base64-encoded binary chunk payload data")
    public String data;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.chunk_index == null)? 0 :this.chunk_index.hashCode()));
        result = ((result* 31)+((this.data == null)? 0 :this.data.hashCode()));
        result = ((result* 31)+((this.event_no == null)? 0 :this.event_no.hashCode()));
        result = ((result* 31)+((this.session_id == null)? 0 :this.session_id.hashCode()));
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
        if ((other instanceof StreamEvents) == false) {
            return false;
        }
        StreamEvents rhs = ((StreamEvents) other);
        return ((((((((this.chunk_index == rhs.chunk_index)||((this.chunk_index!= null)&&this.chunk_index.equals(rhs.chunk_index)))&&((this.data == rhs.data)||((this.data!= null)&&this.data.equals(rhs.data))))&&((this.event_no == rhs.event_no)||((this.event_no!= null)&&this.event_no.equals(rhs.event_no))))&&((this.session_id == rhs.session_id)||((this.session_id!= null)&&this.session_id.equals(rhs.session_id))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.total_chunks == rhs.total_chunks)||((this.total_chunks!= null)&&this.total_chunks.equals(rhs.total_chunks))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
