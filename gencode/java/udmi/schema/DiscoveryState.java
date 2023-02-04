
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery State
 * <p>
 * State for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "networks"
})
@Generated("jsonschema2pojo")
public class DiscoveryState {

    /**
     * Generational marker for enumeration
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for enumeration")
    public Date generation;
    /**
     * Discovery protocol networks
     * 
     */
    @JsonProperty("networks")
    @JsonPropertyDescription("Discovery protocol networks")
    public HashMap<String, NetworkDiscoveryState> networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryState) == false) {
            return false;
        }
        DiscoveryState rhs = ((DiscoveryState) other);
        return (((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks))));
    }

}
