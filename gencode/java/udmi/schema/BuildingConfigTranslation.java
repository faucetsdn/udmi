
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Building Config Translation
 * <p>
 * [Discovery result](../docs/specs/discovery.md) with implicit enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "present_value",
    "units",
    "states"
})
@Generated("jsonschema2pojo")
public class BuildingConfigTranslation {

    /**
     * dotted path to present_value field
     * 
     */
    @JsonProperty("present_value")
    @JsonPropertyDescription("dotted path to present_value field")
    public String present_value;
    @JsonProperty("units")
    public Object units;
    @JsonProperty("states")
    public Object states;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.present_value == null)? 0 :this.present_value.hashCode()));
        result = ((result* 31)+((this.states == null)? 0 :this.states.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BuildingConfigTranslation) == false) {
            return false;
        }
        BuildingConfigTranslation rhs = ((BuildingConfigTranslation) other);
        return ((((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units)))&&((this.present_value == rhs.present_value)||((this.present_value!= null)&&this.present_value.equals(rhs.present_value))))&&((this.states == rhs.states)||((this.states!= null)&&this.states.equals(rhs.states))));
    }

}
