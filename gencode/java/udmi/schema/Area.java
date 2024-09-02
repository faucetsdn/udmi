
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "net_occupied_area_m2",
    "gross_internal_area_m2"
})
@Generated("jsonschema2pojo")
public class Area {

    /**
     * Net Occupied Area of the site in square meters
     * 
     */
    @JsonProperty("net_occupied_area_m2")
    @JsonPropertyDescription("Net Occupied Area of the site in square meters")
    public Double net_occupied_area_m2;
    /**
     * Gross internal Area of the site in square meters
     * 
     */
    @JsonProperty("gross_internal_area_m2")
    @JsonPropertyDescription("Gross internal Area of the site in square meters")
    public Double gross_internal_area_m2;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.gross_internal_area_m2 == null)? 0 :this.gross_internal_area_m2 .hashCode()));
        result = ((result* 31)+((this.net_occupied_area_m2 == null)? 0 :this.net_occupied_area_m2 .hashCode()));
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
        return (((this.gross_internal_area_m2 == rhs.gross_internal_area_m2)||((this.gross_internal_area_m2 != null)&&this.gross_internal_area_m2 .equals(rhs.gross_internal_area_m2)))&&((this.net_occupied_area_m2 == rhs.net_occupied_area_m2)||((this.net_occupied_area_m2 != null)&&this.net_occupied_area_m2 .equals(rhs.net_occupied_area_m2))));
    }

}
