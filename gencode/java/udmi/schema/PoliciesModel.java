
package udmi.schema;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Policies Model
 * <p>
 * Device policies
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "named_policies"
})
public class PoliciesModel {

    /**
     * An array of policies which are applicable to the device
     * 
     */
    @JsonProperty("named_policies")
    @JsonPropertyDescription("An array of policies which are applicable to the device")
    public List<String> named_policies;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.named_policies == null)? 0 :this.named_policies.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PoliciesModel) == false) {
            return false;
        }
        PoliciesModel rhs = ((PoliciesModel) other);
        return ((this.named_policies == rhs.named_policies)||((this.named_policies!= null)&&this.named_policies.equals(rhs.named_policies)));
    }

}
