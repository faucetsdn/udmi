
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
 * Feature Discovery
 * <p>
 * Object representation for for a single feature
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "stage"
})
public class FeatureDiscovery {

    /**
     * FeatureStage
     * <p>
     * Stage of a feature implemenation
     * 
     */
    @JsonProperty("stage")
    @JsonPropertyDescription("Stage of a feature implemenation")
    public FeatureDiscovery.FeatureStage stage;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.stage == null)? 0 :this.stage.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FeatureDiscovery) == false) {
            return false;
        }
        FeatureDiscovery rhs = ((FeatureDiscovery) other);
        return ((this.stage == rhs.stage)||((this.stage!= null)&&this.stage.equals(rhs.stage)));
    }


    /**
     * FeatureStage
     * <p>
     * Stage of a feature implemenation
     * 
     */
    public enum FeatureStage {

        DISABLED("disabled"),
        ALPHA("alpha"),
        PREVIEW("preview"),
        BETA("beta"),
        STABLE("stable");
        private final String value;
        private final static Map<String, FeatureDiscovery.FeatureStage> CONSTANTS = new HashMap<String, FeatureDiscovery.FeatureStage>();

        static {
            for (FeatureDiscovery.FeatureStage c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        FeatureStage(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static FeatureDiscovery.FeatureStage fromValue(String value) {
            FeatureDiscovery.FeatureStage constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
