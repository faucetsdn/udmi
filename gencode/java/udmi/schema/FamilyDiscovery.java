
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
public class FamilyDiscovery {

    /**
     * Device addr in the namespace of the given family
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("Device addr in the namespace of the given family")
    public String addr;
    /**
     * Port number for the family connection (e.g. UDP port for BACnet/IP)
     * 
     */
    @JsonProperty("port")
    @JsonPropertyDescription("Port number for the family connection (e.g. UDP port for BACnet/IP)")
    public Integer port;
    /**
     * Point reference in the namespace of the given family
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Point reference in the namespace of the given family")
    public String ref;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.port == null)? 0 :this.port.hashCode()));
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
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
        return ((((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.port == rhs.port)||((this.port!= null)&&this.port.equals(rhs.port))))&&((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref))));
    }

}
