
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Site Location
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "address",
    "lat",
    "long"
})
public class SiteLocation {

    /**
     * Postal address of the site
     * 
     */
    @JsonProperty("address")
    @JsonPropertyDescription("Postal address of the site")
    public String address;
    /**
     * Latitude of the site in WGS84 coordinates, North of the equator, as indicated by a map marker
     * 
     */
    @JsonProperty("lat")
    @JsonPropertyDescription("Latitude of the site in WGS84 coordinates, North of the equator, as indicated by a map marker")
    public Double lat;
    /**
     * Longitude of the site in WGS84 coordinates, East of Greenwich, as indicated by a map marker
     * 
     */
    @JsonProperty("long")
    @JsonPropertyDescription("Longitude of the site in WGS84 coordinates, East of Greenwich, as indicated by a map marker")
    public Double _long;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.lat == null)? 0 :this.lat.hashCode()));
        result = ((result* 31)+((this._long == null)? 0 :this._long.hashCode()));
        result = ((result* 31)+((this.address == null)? 0 :this.address.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SiteLocation) == false) {
            return false;
        }
        SiteLocation rhs = ((SiteLocation) other);
        return ((((this.lat == rhs.lat)||((this.lat!= null)&&this.lat.equals(rhs.lat)))&&((this._long == rhs._long)||((this._long!= null)&&this._long.equals(rhs._long))))&&((this.address == rhs.address)||((this.address!= null)&&this.address.equals(rhs.address))));
    }

}
