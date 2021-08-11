
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Subsystem Localnet Metadata
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "local_id"
})
@Generated("jsonschema2pojo")
public class SubsystemLocalnetMetadata {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("local_id")
    public String local_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.local_id == null)? 0 :this.local_id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SubsystemLocalnetMetadata) == false) {
            return false;
        }
        SubsystemLocalnetMetadata rhs = ((SubsystemLocalnetMetadata) other);
        return ((this.local_id == rhs.local_id)||((this.local_id!= null)&&this.local_id.equals(rhs.local_id)));
    }

}
