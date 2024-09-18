
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Sequence Validation State
 * <p>
 * Sequence Validation State
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "summary",
    "stage",
    "capabilities",
    "result",
    "status",
    "score",
    "total"
})
public class SequenceValidationState {

    @JsonProperty("summary")
    public java.lang.String summary;
    /**
     * FeatureStage
     * <p>
     * Stage of a feature implemenation
     * 
     */
    @JsonProperty("stage")
    @JsonPropertyDescription("Stage of a feature implemenation")
    public udmi.schema.FeatureDiscovery.FeatureStage stage;
    @JsonProperty("capabilities")
    public Map<String, CapabilityValidationState> capabilities;
    /**
     * Sequence Result
     * <p>
     * 
     * 
     */
    @JsonProperty("result")
    public SequenceValidationState.SequenceResult result;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    @JsonProperty("score")
    public Integer score;
    @JsonProperty("total")
    public Integer total;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.summary == null)? 0 :this.summary.hashCode()));
        result = ((result* 31)+((this.result == null)? 0 :this.result.hashCode()));
        result = ((result* 31)+((this.score == null)? 0 :this.score.hashCode()));
        result = ((result* 31)+((this.total == null)? 0 :this.total.hashCode()));
        result = ((result* 31)+((this.capabilities == null)? 0 :this.capabilities.hashCode()));
        result = ((result* 31)+((this.stage == null)? 0 :this.stage.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SequenceValidationState) == false) {
            return false;
        }
        SequenceValidationState rhs = ((SequenceValidationState) other);
        return ((((((((this.summary == rhs.summary)||((this.summary!= null)&&this.summary.equals(rhs.summary)))&&((this.result == rhs.result)||((this.result!= null)&&this.result.equals(rhs.result))))&&((this.score == rhs.score)||((this.score!= null)&&this.score.equals(rhs.score))))&&((this.total == rhs.total)||((this.total!= null)&&this.total.equals(rhs.total))))&&((this.capabilities == rhs.capabilities)||((this.capabilities!= null)&&this.capabilities.equals(rhs.capabilities))))&&((this.stage == rhs.stage)||((this.stage!= null)&&this.stage.equals(rhs.stage))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Sequence Result
     * <p>
     * 
     * 
     */
    public enum SequenceResult {

        START("start"),
        ERRR("errr"),
        SKIP("skip"),
        PASS("pass"),
        FAIL("fail");
        private final java.lang.String value;
        private final static Map<java.lang.String, SequenceValidationState.SequenceResult> CONSTANTS = new HashMap<java.lang.String, SequenceValidationState.SequenceResult>();

        static {
            for (SequenceValidationState.SequenceResult c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SequenceResult(java.lang.String value) {
            this.value = value;
        }

        @Override
        public java.lang.String toString() {
            return this.value;
        }

        @JsonValue
        public java.lang.String value() {
            return this.value;
        }

        @JsonCreator
        public static SequenceValidationState.SequenceResult fromValue(java.lang.String value) {
            SequenceValidationState.SequenceResult constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
