
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Network Discovery State
 * <p>
 * State for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "active",
    "status"
})
@Generated("jsonschema2pojo")
public class NetworkDiscoveryState {

    /**
     * Generational marker for reporting discovery
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for reporting discovery")
    public Date generation;
    /**
     * Indicates if the discovery process is currently active
     * 
     */
    @JsonProperty("active")
    @JsonPropertyDescription("Indicates if the discovery process is currently active")
    public Boolean active;
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
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.active == null)? 0 :this.active.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof NetworkDiscoveryState) == false) {
            return false;
        }
        NetworkDiscoveryState rhs = ((NetworkDiscoveryState) other);
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.active == rhs.active)||((this.active!= null)&&this.active.equals(rhs.active))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
