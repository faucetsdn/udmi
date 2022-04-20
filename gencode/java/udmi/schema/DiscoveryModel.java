
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Model
 * <p>
 * Discovery target parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "discovery"
})
@Generated("jsonschema2pojo")
public class DiscoveryModel {

    @JsonProperty("discovery")
    public Discovery discovery;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryModel) == false) {
            return false;
        }
        DiscoveryModel rhs = ((DiscoveryModel) other);
        return ((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery)));
    }

}
