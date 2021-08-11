
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blobset Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "blobs"
})
@Generated("jsonschema2pojo")
public class BlobsetConfig__1 {

    @JsonProperty("blobs")
    public HashMap<String, BlobBlobsetConfig> blobs;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.blobs == null)? 0 :this.blobs.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobsetConfig__1) == false) {
            return false;
        }
        BlobsetConfig__1 rhs = ((BlobsetConfig__1) other);
        return ((this.blobs == rhs.blobs)||((this.blobs!= null)&&this.blobs.equals(rhs.blobs)));
    }

}
