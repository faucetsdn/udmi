
package udmi.schema;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
    "active_count",
    "passive_count",
    "status"
})
public class FamilyDiscoveryState {

    /**
     * Generational marker for reporting discovery
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for reporting discovery")
    public Date generation;
    /**
     * Current phase of an active discovery process
     * 
     */
    @JsonProperty("phase")
    @JsonPropertyDescription("Current phase of an active discovery process")
    public FamilyDiscoveryState.Phase phase;
    /**
     * Number of records produced so far for this active scan generation
     * 
     */
    @JsonProperty("active_count")
    @JsonPropertyDescription("Number of records produced so far for this active scan generation")
    public Integer active_count;
    /**
     * Number of passive scan results currently 'on hold'
     * 
     */
    @JsonProperty("passive_count")
    @JsonPropertyDescription("Number of passive scan results currently 'on hold'")
    public Integer passive_count;
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
        result = ((result* 31)+((this.active_count == null)? 0 :this.active_count.hashCode()));
        result = ((result* 31)+((this.passive_count == null)? 0 :this.passive_count.hashCode()));
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
        return ((((((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.phase == rhs.phase)||((this.phase!= null)&&this.phase.equals(rhs.phase))))&&((this.active_count == rhs.active_count)||((this.active_count!= null)&&this.active_count.equals(rhs.active_count))))&&((this.passive_count == rhs.passive_count)||((this.passive_count!= null)&&this.passive_count.equals(rhs.passive_count))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Current phase of an active discovery process
     * 
     */
    public enum Phase {

        STOPPED("stopped"),
        PENDING("pending"),
        ACTIVE("active");
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
