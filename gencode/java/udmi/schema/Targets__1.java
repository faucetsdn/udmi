
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "INVALID_STATE",
    "FAILURE_STATE",
    "APPLIED_STATE"
})
@Generated("jsonschema2pojo")
public class Targets__1 {

    /**
     * Target Testing Metadata
     * <p>
     * 
     * 
     */
    @JsonProperty("INVALID_STATE")
    public TargetTestingMetadata invalid_state;
    /**
     * Target Testing Metadata
     * <p>
     * 
     * 
     */
    @JsonProperty("FAILURE_STATE")
    public TargetTestingMetadata failure_state;
    /**
     * Target Testing Metadata
     * <p>
     * 
     * 
     */
    @JsonProperty("APPLIED_STATE")
    public TargetTestingMetadata applied_state;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.invalid_state == null)? 0 :this.invalid_state.hashCode()));
        result = ((result* 31)+((this.applied_state == null)? 0 :this.applied_state.hashCode()));
        result = ((result* 31)+((this.failure_state == null)? 0 :this.failure_state.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Targets__1) == false) {
            return false;
        }
        Targets__1 rhs = ((Targets__1) other);
        return ((((this.invalid_state == rhs.invalid_state)||((this.invalid_state!= null)&&this.invalid_state.equals(rhs.invalid_state)))&&((this.applied_state == rhs.applied_state)||((this.applied_state!= null)&&this.applied_state.equals(rhs.applied_state))))&&((this.failure_state == rhs.failure_state)||((this.failure_state!= null)&&this.failure_state.equals(rhs.failure_state))));
    }

}
