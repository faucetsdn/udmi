
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Network Discovery Event
 * <p>
 * Discovery information for an individual network.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id"
})
@Generated("jsonschema2pojo")
public class NetworkDiscoveryEvent {

    /**
     * Device id in the namespace of the given network
     * (Required)
     * 
     */
    @JsonProperty("id")
    @JsonPropertyDescription("Device id in the namespace of the given network")
    public String id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof NetworkDiscoveryEvent) == false) {
            return false;
        }
        NetworkDiscoveryEvent rhs = ((NetworkDiscoveryEvent) other);
        return ((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id)));
    }

}
