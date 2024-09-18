
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Virtual Equipment Links
 * <p>
 * Virtual equipment mapping, keyed by guid
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class VirtualEquipmentLinks {


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
        if ((other instanceof VirtualEquipmentLinks) == false) {
            return false;
        }
        VirtualEquipmentLinks rhs = ((VirtualEquipmentLinks) other);
        return true;
    }

}
