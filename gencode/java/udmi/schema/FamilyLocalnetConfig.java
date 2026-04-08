
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Family Localnet Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subsystem"
})
public class FamilyLocalnetConfig {

    @JsonProperty("subsystem")
    public String subsystem;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
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
        return ((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)));
    }

}
