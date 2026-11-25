
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blobset State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlobsetState {

    /**
     * Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("Not included in messages published by devices. Part of message subblocks within cloud pipeline. RFC 3339 Timestamp the payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("blobs")
    public HashMap<String, BlobBlobsetState> blobs;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.blobs == null)? 0 :this.blobs.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobsetState) == false) {
            return false;
        }
        BlobsetState rhs = ((BlobsetState) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.blobs == rhs.blobs)||((this.blobs!= null)&&this.blobs.equals(rhs.blobs))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
