
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "value",
    "unit"
})
@Generated("jsonschema2pojo")
public class Unit_cost {

    /**
     * Unit cost for electricity consumption
     * 
     */
    @JsonProperty("value")
    @JsonPropertyDescription("Unit cost for electricity consumption")
    public Double value;
    /**
     * Unit used for measuring the unit cost of electricity consumption
     * 
     */
    @JsonProperty("unit")
    @JsonPropertyDescription("Unit used for measuring the unit cost of electricity consumption")
    public String unit;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.value == null)? 0 :this.value.hashCode()));
        result = ((result* 31)+((this.unit == null)? 0 :this.unit.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Unit_cost) == false) {
            return false;
        }
        Unit_cost rhs = ((Unit_cost) other);
        return (((this.value == rhs.value)||((this.value!= null)&&this.value.equals(rhs.value)))&&((this.unit == rhs.unit)||((this.unit!= null)&&this.unit.equals(rhs.unit))));
    }

}
