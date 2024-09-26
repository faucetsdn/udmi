
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "lat",
    "long",
    "alt_m"
})
public class Coordinates {

    /**
     * The `latitude` coordinate of the device in the WGS84 coordinate system
     * 
     */
    @JsonProperty("lat")
    @JsonPropertyDescription("The `latitude` coordinate of the device in the WGS84 coordinate system")
    public Double lat;
    /**
     * The `longitude` coordinate of the device in the WGS84 coordinate system
     * 
     */
    @JsonProperty("long")
    @JsonPropertyDescription("The `longitude` coordinate of the device in the WGS84 coordinate system")
    public Double _long;
    /**
     * The `altitude` coordinate of the device in meters
     * 
     */
    @JsonProperty("alt_m")
    @JsonPropertyDescription("The `altitude` coordinate of the device in meters")
    public Double alt_m;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.lat == null)? 0 :this.lat.hashCode()));
        result = ((result* 31)+((this._long == null)? 0 :this._long.hashCode()));
        result = ((result* 31)+((this.alt_m == null)? 0 :this.alt_m.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Coordinates) == false) {
            return false;
        }
        Coordinates rhs = ((Coordinates) other);
        return ((((this.lat == rhs.lat)||((this.lat!= null)&&this.lat.equals(rhs.lat)))&&((this._long == rhs._long)||((this._long!= null)&&this._long.equals(rhs._long))))&&((this.alt_m == rhs.alt_m)||((this.alt_m!= null)&&this.alt_m.equals(rhs.alt_m))));
    }

}
