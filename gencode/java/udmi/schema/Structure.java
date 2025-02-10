
package udmi.schema;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "point",
    "families"
})
public class Structure {

    /**
     * Family Discovery
     * <p>
     * Discovery information for a protocol family.
     * 
     */
    @JsonProperty("point")
    @JsonPropertyDescription("Discovery information for a protocol family.")
    public FamilyDiscovery point;
    /**
     * Reference links to alternate families
     * 
     */
    @JsonProperty("families")
    @JsonPropertyDescription("Reference links to alternate families")
    public Map<String, FamilyDiscovery> families;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.point == null)? 0 :this.point.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Structure) == false) {
            return false;
        }
        Structure rhs = ((Structure) other);
        return (((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families)))&&((this.point == rhs.point)||((this.point!= null)&&this.point.equals(rhs.point))));
    }

}
