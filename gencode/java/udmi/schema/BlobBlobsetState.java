
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blob Blobset State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "phase",
    "status"
})
@Generated("jsonschema2pojo")
public class BlobBlobsetState {

    /**
     * BlobPhase
     * <p>
     * Phase for the management of a configuration blob.
     * (Required)
     * 
     */
    @JsonProperty("phase")
    @JsonPropertyDescription("Phase for the management of a configuration blob.")
    public udmi.schema.BlobBlobsetConfig.BlobPhase phase;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.phase == null)? 0 :this.phase.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobBlobsetState) == false) {
            return false;
        }
        BlobBlobsetState rhs = ((BlobBlobsetState) other);
        return (((this.phase == rhs.phase)||((this.phase!= null)&&this.phase.equals(rhs.phase)))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
