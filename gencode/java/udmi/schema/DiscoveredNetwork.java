
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovered Network
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families"
})
@Generated("jsonschema2pojo")
public class DiscoveredNetwork {

    @JsonProperty("families")
    public HashMap<String, DiscoveredNetworkFamily> families;

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
        if ((other instanceof DiscoveredNetwork) == false) {
            return false;
        }
        DiscoveredNetwork rhs = ((DiscoveredNetwork) other);
        return ((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)));
    }

}
