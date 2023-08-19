
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Registry Udmi State
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "iot_provider"
})
@Generated("jsonschema2pojo")
public class RegistryUdmiState {

    /**
     * Iot Provider
     * <p>
     * 
     * 
     */
    @JsonProperty("iot_provider")
    public udmi.schema.IotAccess.IotProvider iot_provider;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.iot_provider == null)? 0 :this.iot_provider.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof RegistryUdmiState) == false) {
            return false;
        }
        RegistryUdmiState rhs = ((RegistryUdmiState) other);
        return ((this.iot_provider == rhs.iot_provider)||((this.iot_provider!= null)&&this.iot_provider.equals(rhs.iot_provider)));
    }

}
