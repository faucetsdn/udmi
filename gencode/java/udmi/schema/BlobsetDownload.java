
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blobset Download
 * <p>
 * Direct cloud-to-device payload delivery for blobset configurations over the download/blobset channel.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlobsetDownload {

    /**
     * RFC 3339 UTC timestamp the download payload was generated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 UTC timestamp the download payload was generated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Map of system blobset keys to their provisioned endpoint configuration payloads
     * (Required)
     * 
     */
    @JsonProperty("blobs")
    @JsonPropertyDescription("Map of system blobset keys to their provisioned endpoint configuration payloads")
    public HashMap<String, EndpointConfiguration> blobs;

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
        if ((other instanceof BlobsetDownload) == false) {
            return false;
        }
        BlobsetDownload rhs = ((BlobsetDownload) other);
        return ((((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version)))&&((this.blobs == rhs.blobs)||((this.blobs!= null)&&this.blobs.equals(rhs.blobs))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
