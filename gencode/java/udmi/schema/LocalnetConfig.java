
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet Config
 * <p>
 * Used to describe device local network parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families"
})
@Generated("jsonschema2pojo")
public class LocalnetConfig {

    /**
     * Family Reference
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("families")
    public Object families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LocalnetConfig) == false) {
            return false;
        }
        LocalnetConfig rhs = ((LocalnetConfig) other);
        return ((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)));
    }

}
