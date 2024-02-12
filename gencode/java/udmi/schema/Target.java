
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * For proxied devices, this represents the target proxy device address for use by the gateway
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "family"
})
@Generated("jsonschema2pojo")
public class Target {

    @JsonProperty("family")
    public String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Target) == false) {
            return false;
        }
        Target rhs = ((Target) other);
        return ((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family)));
    }

}
