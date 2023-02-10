
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Feature Enumeration Event
 * <p>
 * Object representation for for a single feature enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "stage"
})
@Generated("jsonschema2pojo")
public class FeatureEnumerationEvent {

    /**
     * Feature implementation stage
     * 
     */
    @JsonProperty("stage")
    @JsonPropertyDescription("Feature implementation stage")
    public FeatureEnumerationEvent.Stage stage;

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
        if ((other instanceof FeatureEnumerationEvent) == false) {
            return false;
        }
        FeatureEnumerationEvent rhs = ((FeatureEnumerationEvent) other);
        return ((this.stage == rhs.stage)||((this.stage!= null)&&this.stage.equals(rhs.stage)));
    }


    /**
     * Feature implementation stage
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Stage {

        MISSING("missing"),
        ALPHA("alpha"),
        BETA("beta"),
        STABLE("stable");
        private final String value;
        private final static Map<String, FeatureEnumerationEvent.Stage> CONSTANTS = new HashMap<String, FeatureEnumerationEvent.Stage>();

        static {
            for (FeatureEnumerationEvent.Stage c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Stage(String value) {
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
        public static FeatureEnumerationEvent.Stage fromValue(String value) {
            FeatureEnumerationEvent.Stage constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
