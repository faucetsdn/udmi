
package udmi.schema;

import java.util.HashMap;
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
    "networks"
})
@Generated("jsonschema2pojo")
public class DiscoveryModel {

    @JsonProperty("networks")
    public HashMap<String, NetworkDiscoveryTestingModel> networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
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
        return ((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks)));
    }

}
