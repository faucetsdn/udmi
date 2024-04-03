
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Site Metadata
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "site_id",
    "name",
    "location"
})
@Generated("jsonschema2pojo")
public class SiteMetadata {

    /**
     * RFC 3339 timestamp the site metadata was last updated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp the site metadata was last updated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public String version;
    /**
     * Identifier for the site or building
     * 
     */
    @JsonProperty("site_id")
    @JsonPropertyDescription("Identifier for the site or building")
    public String site_id;
    /**
     * Name of the site or building
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the site or building")
    public String name;
    @JsonProperty("location")
    public Location location;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.site_id == null)? 0 :this.site_id.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SiteMetadata) == false) {
            return false;
        }
        SiteMetadata rhs = ((SiteMetadata) other);
        return ((((((this.site_id == rhs.site_id)||((this.site_id!= null)&&this.site_id.equals(rhs.site_id)))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
