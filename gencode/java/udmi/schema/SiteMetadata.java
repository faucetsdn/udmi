
package udmi.schema;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


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
    "site",
    "name",
    "tags",
    "location",
    "area",
    "site_folder",
    "source_repo",
    "device_count",
    "validated_count"
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
    @JsonProperty("site")
    @JsonPropertyDescription("Identifier for the site or building")
    public String site;
    /**
     * Name of the site or building
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the site or building")
    public String name;
    /**
     * Tags associated with the site
     * 
     */
    @JsonProperty("tags")
    @JsonDeserialize(as = java.util.LinkedHashSet.class)
    @JsonPropertyDescription("Tags associated with the site")
    public Set<Object> tags = new LinkedHashSet<Object>();
    @JsonProperty("location")
    public Location location;
    @JsonProperty("area")
    public Area area;
    /**
     * Folder where the UDMI site model is stored
     * 
     */
    @JsonProperty("site_folder")
    @JsonPropertyDescription("Folder where the UDMI site model is stored")
    public String site_folder;
    /**
     * Source repository where the UDMI site model is stored
     * 
     */
    @JsonProperty("source_repo")
    @JsonPropertyDescription("Source repository where the UDMI site model is stored")
    public String source_repo;
    /**
     * Number of devices in the UDMI site model
     * 
     */
    @JsonProperty("device_count")
    @JsonPropertyDescription("Number of devices in the UDMI site model")
    public Double device_count;
    /**
     * Number of validated devices in the UDMI site model
     * 
     */
    @JsonProperty("validated_count")
    @JsonPropertyDescription("Number of validated devices in the UDMI site model")
    public Double validated_count;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.area == null)? 0 :this.area.hashCode()));
        result = ((result* 31)+((this.site == null)? 0 :this.site.hashCode()));
        result = ((result* 31)+((this.source_repo == null)? 0 :this.source_repo.hashCode()));
        result = ((result* 31)+((this.site_folder == null)? 0 :this.site_folder.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.validated_count == null)? 0 :this.validated_count.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.device_count == null)? 0 :this.device_count.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        result = ((result* 31)+((this.tags == null)? 0 :this.tags.hashCode()));
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
        return ((((((((((((this.area == rhs.area)||((this.area!= null)&&this.area.equals(rhs.area)))&&((this.site == rhs.site)||((this.site!= null)&&this.site.equals(rhs.site))))&&((this.source_repo == rhs.source_repo)||((this.source_repo!= null)&&this.source_repo.equals(rhs.source_repo))))&&((this.site_folder == rhs.site_folder)||((this.site_folder!= null)&&this.site_folder.equals(rhs.site_folder))))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.validated_count == rhs.validated_count)||((this.validated_count!= null)&&this.validated_count.equals(rhs.validated_count))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.device_count == rhs.device_count)||((this.device_count!= null)&&this.device_count.equals(rhs.device_count))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.tags == rhs.tags)||((this.tags!= null)&&this.tags.equals(rhs.tags))));
    }

}
