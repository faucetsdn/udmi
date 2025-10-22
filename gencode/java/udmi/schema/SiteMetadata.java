
package udmi.schema;

import java.util.Date;
import java.util.Map;
import java.util.Set;
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
    "strict_warnings",
    "location",
    "links",
    "externals",
    "parameters"
})
public class SiteMetadata {

    /**
     * RFC 3339 timestamp when the site metadata was last updated
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("RFC 3339 timestamp when the site metadata was last updated")
    public Date timestamp;
    /**
     * Version of the UDMI schema
     * (Required)
     * 
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the UDMI schema")
    public java.lang.String version;
    /**
     * Identifier for the site or building
     * 
     */
    @JsonProperty("site")
    @JsonPropertyDescription("Identifier for the site or building")
    public java.lang.String site;
    /**
     * Name of the site or building
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Name of the site or building")
    public java.lang.String name;
    /**
     * Tags associated with the site
     * 
     */
    @JsonProperty("tags")
    @JsonDeserialize(as = java.util.LinkedHashSet.class)
    @JsonPropertyDescription("Tags associated with the site")
    public Set<Object> tags;
    /**
     * Enable strict checking and treat warnings as errors
     * 
     */
    @JsonProperty("strict_warnings")
    @JsonPropertyDescription("Enable strict checking and treat warnings as errors")
    public Boolean strict_warnings;
    /**
     * Site Location
     * <p>
     * 
     * 
     */
    @JsonProperty("location")
    public SiteLocation location;
    /**
     * Site Links
     * <p>
     * 
     * 
     */
    @JsonProperty("links")
    public SiteLinks links;
    /**
     * Information specific to how links to external systems are handled
     * 
     */
    @JsonProperty("externals")
    @JsonPropertyDescription("Information specific to how links to external systems are handled")
    public Map<String, LinkExternalsSite> externals;
    @JsonProperty("parameters")
    public Parameters parameters;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.site == null)? 0 :this.site.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.links == null)? 0 :this.links.hashCode()));
        result = ((result* 31)+((this.externals == null)? 0 :this.externals.hashCode()));
        result = ((result* 31)+((this.version == null)? 0 :this.version.hashCode()));
        result = ((result* 31)+((this.parameters == null)? 0 :this.parameters.hashCode()));
        result = ((result* 31)+((this.strict_warnings == null)? 0 :this.strict_warnings.hashCode()));
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
        return (((((((((((this.site == rhs.site)||((this.site!= null)&&this.site.equals(rhs.site)))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.links == rhs.links)||((this.links!= null)&&this.links.equals(rhs.links))))&&((this.externals == rhs.externals)||((this.externals!= null)&&this.externals.equals(rhs.externals))))&&((this.version == rhs.version)||((this.version!= null)&&this.version.equals(rhs.version))))&&((this.parameters == rhs.parameters)||((this.parameters!= null)&&this.parameters.equals(rhs.parameters))))&&((this.strict_warnings == rhs.strict_warnings)||((this.strict_warnings!= null)&&this.strict_warnings.equals(rhs.strict_warnings))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))))&&((this.tags == rhs.tags)||((this.tags!= null)&&this.tags.equals(rhs.tags))));
    }

}
