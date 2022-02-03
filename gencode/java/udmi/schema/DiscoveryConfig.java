
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Discovery Config
 * <p>
 * Configuration for [discovery](../docs/specs/discovery.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "generation",
    "enumeration"
})
@Generated("jsonschema2pojo")
public class DiscoveryConfig {

    /**
     * Generational marker for controlling discovery.
     * 
     */
    @JsonProperty("generation")
    @JsonPropertyDescription("Generational marker for controlling discovery.")
    public Date generation;
    @JsonProperty("enumeration")
    public Enumeration enumeration;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.generation == null)? 0 :this.generation.hashCode()));
        result = ((result* 31)+((this.enumeration == null)? 0 :this.enumeration.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DiscoveryConfig) == false) {
            return false;
        }
        DiscoveryConfig rhs = ((DiscoveryConfig) other);
        return (((this.generation == rhs.generation)||((this.generation!= null)&&this.generation.equals(rhs.generation)))&&((this.enumeration == rhs.enumeration)||((this.enumeration!= null)&&this.enumeration.equals(rhs.enumeration))));
    }

}
