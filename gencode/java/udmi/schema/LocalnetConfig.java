
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Localnet Config
 * <p>
 * Used to describe device local network parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "networks"
})
@Generated("jsonschema2pojo")
public class LocalnetConfig {

    /**
     * Network Reference
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("networks")
    public Object networks;

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
        if ((other instanceof LocalnetConfig) == false) {
            return false;
        }
        LocalnetConfig rhs = ((LocalnetConfig) other);
        return ((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks)));
    }

}
