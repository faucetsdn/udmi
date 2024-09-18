
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "net_occupied_area",
    "gross_internal_area"
})
public class Area {

    /**
     * Dimension
     * <p>
     * Dimensions with a value and a unit
     * 
     */
    @JsonProperty("net_occupied_area")
    @JsonPropertyDescription("Dimensions with a value and a unit")
    public Dimension net_occupied_area;
    /**
     * Dimension
     * <p>
     * Dimensions with a value and a unit
     * 
     */
    @JsonProperty("gross_internal_area")
    @JsonPropertyDescription("Dimensions with a value and a unit")
    public Dimension gross_internal_area;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.gross_internal_area == null)? 0 :this.gross_internal_area.hashCode()));
        result = ((result* 31)+((this.net_occupied_area == null)? 0 :this.net_occupied_area.hashCode()));
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
        return (((this.gross_internal_area == rhs.gross_internal_area)||((this.gross_internal_area!= null)&&this.gross_internal_area.equals(rhs.gross_internal_area)))&&((this.net_occupied_area == rhs.net_occupied_area)||((this.net_occupied_area!= null)&&this.net_occupied_area.equals(rhs.net_occupied_area))));
    }

}
