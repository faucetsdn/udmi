
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet Metadata
 * <p>
 * Used to describe device local network parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subsystem"
})
@Generated("jsonschema2pojo")
public class LocalnetMetadata__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subsystem")
    public HashMap<String, SubsystemLocalnetMetadata> subsystem;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LocalnetMetadata__1) == false) {
            return false;
        }
        LocalnetMetadata__1 rhs = ((LocalnetMetadata__1) other);
        return ((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)));
    }

}
