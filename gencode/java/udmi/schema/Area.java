
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "m2",
    "sqf"
})
@Generated("jsonschema2pojo")
public class Area {

    /**
     * Area of the site in square meters
     * 
     */
    @JsonProperty("m2")
    @JsonPropertyDescription("Area of the site in square meters")
    public Double m2;
    /**
     * Area of the site in square feet
     * 
     */
    @JsonProperty("sqf")
    @JsonPropertyDescription("Area of the site in square feet")
    public Double sqf;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.m2 == null)? 0 :this.m2 .hashCode()));
        result = ((result* 31)+((this.sqf == null)? 0 :this.sqf.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Area) == false) {
            return false;
        }
        Area rhs = ((Area) other);
        return (((this.m2 == rhs.m2)||((this.m2 != null)&&this.m2 .equals(rhs.m2)))&&((this.sqf == rhs.sqf)||((this.sqf!= null)&&this.sqf.equals(rhs.sqf))));
    }

}
