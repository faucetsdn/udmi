
package udmi.schema;

import java.util.HashMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet Model
 * <p>
 * Used to describe device local network parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families"
})
public class LocalnetModel {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("families")
    public HashMap<String, FamilyLocalnetModel> families;

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
        if ((other instanceof LocalnetModel) == false) {
            return false;
        }
        LocalnetModel rhs = ((LocalnetModel) other);
        return ((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)));
    }

}
