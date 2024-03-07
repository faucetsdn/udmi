
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "lat",
    "lon"
})
@Generated("jsonschema2pojo")
public class Coordinates {

    /**
     * The `lattitude` coordinate of the device in the WSG84 coordinate system
     * 
     */
    @JsonProperty("lat")
    @JsonPropertyDescription("The `lattitude` coordinate of the device in the WSG84 coordinate system")
    public Double lat;
    /**
     * The `longitude` coordinate of the device in the WSG84 coordinate system
     * 
     */
    @JsonProperty("lon")
    @JsonPropertyDescription("The `longitude` coordinate of the device in the WSG84 coordinate system")
    public Double lon;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.lat == null)? 0 :this.lat.hashCode()));
        result = ((result* 31)+((this.lon == null)? 0 :this.lon.hashCode()));
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
        return (((this.lat == rhs.lat)||((this.lat!= null)&&this.lat.equals(rhs.lat)))&&((this.lon == rhs.lon)||((this.lon!= null)&&this.lon.equals(rhs.lon))));
    }

}
