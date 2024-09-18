
package udmi.schema;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Feature Validation State
 * <p>
 * Feature validation state
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "sequences"
})
public class FeatureValidationState {

    @JsonProperty("sequences")
    public Map<String, SequenceValidationState> sequences;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.sequences == null)? 0 :this.sequences.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FeatureValidationState) == false) {
            return false;
        }
        FeatureValidationState rhs = ((FeatureValidationState) other);
        return ((this.sequences == rhs.sequences)||((this.sequences!= null)&&this.sequences.equals(rhs.sequences)));
    }

}
