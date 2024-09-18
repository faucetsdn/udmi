
package udmi.schema;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import udmi.schema.FeatureDiscovery.FeatureStage;


/**
 * Schema Validation State
 * <p>
 * Schema validation state
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "stages",
    "sequences"
})
public class SchemaValidationState {

    @JsonProperty("stages")
    public Map<FeatureStage, Entry> stages;
    @JsonProperty("sequences")
    public Map<String, SequenceValidationState> sequences;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.stages == null)? 0 :this.stages.hashCode()));
        result = ((result* 31)+((this.sequences == null)? 0 :this.sequences.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SchemaValidationState) == false) {
            return false;
        }
        SchemaValidationState rhs = ((SchemaValidationState) other);
        return (((this.stages == rhs.stages)||((this.stages!= null)&&this.stages.equals(rhs.stages)))&&((this.sequences == rhs.sequences)||((this.sequences!= null)&&this.sequences.equals(rhs.sequences))));
    }

}
