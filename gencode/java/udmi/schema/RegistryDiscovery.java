
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Registry Discovery
 * <p>
 * Information about an individual registry.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "last_seen",
    "last_update"
})
@Generated("jsonschema2pojo")
public class RegistryDiscovery {

    /**
     * Timestamp of when a message was last seen for this registry
     * 
     */
    @JsonProperty("last_seen")
    @JsonPropertyDescription("Timestamp of when a message was last seen for this registry")
    public Date last_seen;
    /**
     * Last update of the registry information
     * 
     */
    @JsonProperty("last_update")
    @JsonPropertyDescription("Last update of the registry information")
    public Date last_update;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.last_seen == null)? 0 :this.last_seen.hashCode()));
        result = ((result* 31)+((this.last_update == null)? 0 :this.last_update.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof RegistryDiscovery) == false) {
            return false;
        }
        RegistryDiscovery rhs = ((RegistryDiscovery) other);
        return (((this.last_seen == rhs.last_seen)||((this.last_seen!= null)&&this.last_seen.equals(rhs.last_seen)))&&((this.last_update == rhs.last_update)||((this.last_update!= null)&&this.last_update.equals(rhs.last_update))));
    }

}
