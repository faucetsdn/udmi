
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Device Discovery Event
 * <p>
 * Information about an individual device scan result.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
@Generated("jsonschema2pojo")
public class DeviceDiscoveryEvent {


    @Override
    public int hashCode() {
        int result = 1;
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DeviceDiscoveryEvent) == false) {
            return false;
        }
        DeviceDiscoveryEvent rhs = ((DeviceDiscoveryEvent) other);
        return true;
    }

}
