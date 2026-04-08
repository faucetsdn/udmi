
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Localnet Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "family"
})
public class FamilyLocalnetConfig {

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
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyLocalnetConfig) == false) {
            return false;
        }
        FamilyLocalnetConfig rhs = ((FamilyLocalnetConfig) other);
        return ((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family)));
    }

}
