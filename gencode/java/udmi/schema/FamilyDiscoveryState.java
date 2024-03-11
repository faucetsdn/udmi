
package udmi.schema;

import java.util.Date;
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
 * Family Discovery State
 * <p>
 * State for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "phase",
    "status"
})
@Generated("jsonschema2pojo")
public class FamilyDiscoveryState {

    /**
     * Generational marker for reporting discovery
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for reporting discovery")
    public Date generation;
    /**
     * Current phase of the discovery process
     * 
     */
    @JsonProperty("phase")
    @JsonPropertyDescription("Current phase of the discovery process")
    public FamilyDiscoveryState.Phase phase;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.phase == null)? 0 :this.phase.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FamilyDiscoveryState) == false) {
            return false;
        }
        FamilyDiscoveryState rhs = ((FamilyDiscoveryState) other);
        return ((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.phase == rhs.phase)||((this.phase!= null)&&this.phase.equals(rhs.phase))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Current phase of the discovery process
     * 
     */
    @Generated("jsonschema2pojo")
    public enum Phase {

        PENDING("pending"),
        ACTIVE("active"),
        STOPPED("stopped"),
        DONE("done");
        private final String value;
        private final static Map<String, FamilyDiscoveryState.Phase> CONSTANTS = new HashMap<String, FamilyDiscoveryState.Phase>();

        static {
            for (FamilyDiscoveryState.Phase c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Phase(String value) {
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
        public static FamilyDiscoveryState.Phase fromValue(String value) {
            FamilyDiscoveryState.Phase constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
