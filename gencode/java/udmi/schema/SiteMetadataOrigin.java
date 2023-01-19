
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Site Metadata Origin
 * <p>
 * Locaition of the site origin datum point to which all x/y/z coordinates are relative to
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "lat",
    "long",
    "alt",
    "orientation"
})
@Generated("jsonschema2pojo")
public class SiteMetadataOrigin {

    /**
     * Latitude of the site origin datum point in WGS84 coordinates
     * 
     */
    @JsonProperty("lat")
    @JsonPropertyDescription("Latitude of the site origin datum point in WGS84 coordinates")
    public Double lat;
    /**
     * Longitude of the site origin datum point in WGS84 coordinates
     * 
     */
    @JsonProperty("long")
    @JsonPropertyDescription("Longitude of the site origin datum point in WGS84 coordinates")
    public Double _long;
    /**
     * Altitude of the site origin datum point in meters
     * 
     */
    @JsonProperty("alt")
    @JsonPropertyDescription("Altitude of the site origin datum point in meters")
    public Double alt;
    /**
     * Orientation of site measured clockwise from North
     * 
     */
    @JsonProperty("orientation")
    @JsonPropertyDescription("Orientation of site measured clockwise from North")
    public Double orientation;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.alt == null)? 0 :this.alt.hashCode()));
        result = ((result* 31)+((this.orientation == null)? 0 :this.orientation.hashCode()));
        result = ((result* 31)+((this.lat == null)? 0 :this.lat.hashCode()));
        result = ((result* 31)+((this._long == null)? 0 :this._long.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SiteMetadataOrigin) == false) {
            return false;
        }
        SiteMetadataOrigin rhs = ((SiteMetadataOrigin) other);
        return (((((this.alt == rhs.alt)||((this.alt!= null)&&this.alt.equals(rhs.alt)))&&((this.orientation == rhs.orientation)||((this.orientation!= null)&&this.orientation.equals(rhs.orientation))))&&((this.lat == rhs.lat)||((this.lat!= null)&&this.lat.equals(rhs.lat))))&&((this._long == rhs._long)||((this._long!= null)&&this._long.equals(rhs._long))));
    }

}
