
package udmi.schema;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Policy Model
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "rule_sets"
})
public class PolicyModel {

    /**
     * An array of rule sets which are applicable to the device
     * 
     */
    @JsonProperty("rule_sets")
    @JsonPropertyDescription("An array of rule sets which are applicable to the device")
    public List<String> rule_sets;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.rule_sets == null)? 0 :this.rule_sets.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PolicyModel) == false) {
            return false;
        }
        PolicyModel rhs = ((PolicyModel) other);
        return ((this.rule_sets == rhs.rule_sets)||((this.rule_sets!= null)&&this.rule_sets.equals(rhs.rule_sets)));
    }

}
