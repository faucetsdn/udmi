
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families"
})
@Generated("jsonschema2pojo")
public class LocalnetState {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("families")
    public HashMap<String, FamilyLocalnetState> families;

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
        if ((other instanceof LocalnetState) == false) {
            return false;
        }
        LocalnetState rhs = ((LocalnetState) other);
        return ((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)));
    }

}
