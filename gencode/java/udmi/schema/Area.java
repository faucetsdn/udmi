
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "net_occupied_area",
    "gross_internal_area"
})
@Generated("jsonschema2pojo")
public class Area {

    @JsonProperty("net_occupied_area")
    public Net_occupied_area net_occupied_area;
    @JsonProperty("gross_internal_area")
    public Gross_internal_area gross_internal_area;

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
