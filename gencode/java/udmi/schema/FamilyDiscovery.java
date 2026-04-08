
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
    "addr",
    "ref",
    "family"
})
public class FamilyDiscovery {

    /**
     * Device addr in the namespace of the given family
     * 
     */
    @JsonProperty("addr")
    @JsonPropertyDescription("Device addr in the namespace of the given family")
    public String addr;
    /**
     * Point reference in the namespace of the given family
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Point reference in the namespace of the given family")
    public String ref;
    /**
     * The family designator, used only when the entry is not keyed in a family map
     *
     */
    @JsonProperty("family")
    @JsonPropertyDescription("The family designator, used only when the entry is not keyed in a family map")
    public String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.addr == null)? 0 :this.addr.hashCode()));
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
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
        return ((((this.addr == rhs.addr)||((this.addr!= null)&&this.addr.equals(rhs.addr)))&&((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family))))&&((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref))));
    }

}
