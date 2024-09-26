
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "area",
    "electricity",
    "gas",
    "water"
})
public class Parameters {

    @JsonProperty("area")
    public Area area;
    @JsonProperty("electricity")
    public Electricity electricity;
    @JsonProperty("gas")
    public Gas gas;
    @JsonProperty("water")
    public Water water;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.area == null)? 0 :this.area.hashCode()));
        result = ((result* 31)+((this.gas == null)? 0 :this.gas.hashCode()));
        result = ((result* 31)+((this.electricity == null)? 0 :this.electricity.hashCode()));
        result = ((result* 31)+((this.water == null)? 0 :this.water.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Parameters) == false) {
            return false;
        }
        Parameters rhs = ((Parameters) other);
        return (((((this.area == rhs.area)||((this.area!= null)&&this.area.equals(rhs.area)))&&((this.gas == rhs.gas)||((this.gas!= null)&&this.gas.equals(rhs.gas))))&&((this.electricity == rhs.electricity)||((this.electricity!= null)&&this.electricity.equals(rhs.electricity))))&&((this.water == rhs.water)||((this.water!= null)&&this.water.equals(rhs.water))));
    }

}
