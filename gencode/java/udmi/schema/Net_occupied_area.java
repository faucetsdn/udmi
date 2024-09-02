
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
public class Net_occupied_area {

    /**
     * Net Occupied Area of the site
     * 
     */
    @JsonProperty("value")
    @JsonPropertyDescription("Net Occupied Area of the site")
    public Double value;
    /**
     * Unit used for carbon the Net Occupied Area
     * 
     */
    @JsonProperty("unit")
    @JsonPropertyDescription("Unit used for carbon the Net Occupied Area")
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
        if ((other instanceof Net_occupied_area) == false) {
            return false;
        }
        Net_occupied_area rhs = ((Net_occupied_area) other);
        return (((this.value == rhs.value)||((this.value!= null)&&this.value.equals(rhs.value)))&&((this.unit == rhs.unit)||((this.unit!= null)&&this.unit.equals(rhs.unit))));
    }

}
