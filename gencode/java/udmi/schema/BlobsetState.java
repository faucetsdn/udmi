
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blobset State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "state_etag",
    "blobs"
})
@Generated("jsonschema2pojo")
public class BlobsetState {

    @JsonProperty("state_etag")
    public java.lang.String state_etag;
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
        result = ((result* 31)+((this.blobs == null)? 0 :this.blobs.hashCode()));
        result = ((result* 31)+((this.state_etag == null)? 0 :this.state_etag.hashCode()));
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
        return (((this.blobs == rhs.blobs)||((this.blobs!= null)&&this.blobs.equals(rhs.blobs)))&&((this.state_etag == rhs.state_etag)||((this.state_etag!= null)&&this.state_etag.equals(rhs.state_etag))));
    }

}
