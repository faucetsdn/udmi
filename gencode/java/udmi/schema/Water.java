
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "unit_cost"
})
public class Water {

    /**
     * Dimension
     * <p>
     * Dimensions with a value and a unit
     * 
     */
    @JsonProperty("unit_cost")
    @JsonPropertyDescription("Dimensions with a value and a unit")
    public Dimension unit_cost;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.unit_cost == null)? 0 :this.unit_cost.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Water) == false) {
            return false;
        }
        Water rhs = ((Water) other);
        return ((this.unit_cost == rhs.unit_cost)||((this.unit_cost!= null)&&this.unit_cost.equals(rhs.unit_cost)));
    }

}
