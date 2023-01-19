
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
 * Information about the 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "version",
    "id",
    "name",
    "location",
    "origin"
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
    @JsonProperty("id")
    @JsonPropertyDescription("Identifier for the site or building")
    public String id;
    /**
     * Name of the site or building
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the site or building")
    public String name;
    /**
     * Site Metadata Location
     * <p>
     * Location of the site or building
     * 
     */
    @JsonProperty("location")
    @JsonPropertyDescription("Location of the site or building")
    public SiteMetadataLocation location;
    /**
     * Site Metadata Origin
     * <p>
     * Locaition of the site origin datum point to which all x/y/z coordinates are relative to
     * 
     */
    @JsonProperty("origin")
    @JsonPropertyDescription("Locaition of the site origin datum point to which all x/y/z coordinates are relative to")
    public SiteMetadataOrigin origin;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.origin == null)? 0 :this.origin.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
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
        return (((((((this.origin == rhs.origin)||((this.origin!= null)&&this.origin.equals(rhs.origin)))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
