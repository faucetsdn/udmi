
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Discovery
 * <p>
 * Discovery information for a protocol family.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "addr"
})
public class FamilyDiscovery {

    /**
     * Device addr in the namespace of the given family
     * (Required)
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("Device addr in the namespace of the given family")
    public String addr;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyDiscovery) == false) {
            return false;
        }
        FamilyDiscovery rhs = ((FamilyDiscovery) other);
        return ((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)));
    }

}
