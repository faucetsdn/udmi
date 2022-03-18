
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Blob Enumeration Event
 * <p>
 * Object representation for for a single blob enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "description",
    "firmware_set"
})
@Generated("jsonschema2pojo")
public class BlobEnumerationEvent {

    /**
     * Description of this blob
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Description of this blob")
    public String description;
    /**
     * Indicating if this blob is part of the device's firmware set
     * 
     */
    @JsonProperty("firmware_set")
    @JsonPropertyDescription("Indicating if this blob is part of the device's firmware set")
    public Boolean firmware_set;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.firmware_set == null)? 0 :this.firmware_set.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BlobEnumerationEvent) == false) {
            return false;
        }
        BlobEnumerationEvent rhs = ((BlobEnumerationEvent) other);
        return (((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description)))&&((this.firmware_set == rhs.firmware_set)||((this.firmware_set!= null)&&this.firmware_set.equals(rhs.firmware_set))));
    }

}
