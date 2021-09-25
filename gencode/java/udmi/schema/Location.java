
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Properties the expected physical location of the device.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "site",
    "section",
    "position"
})
@Generated("jsonschema2pojo")
public class Location {

    /**
     * The site name according to the site model in which a device is installed in
     * (Required)
     * 
     */
    @JsonProperty("site")
    @JsonPropertyDescription("The site name according to the site model in which a device is installed in")
    public String site;
    @JsonProperty("section")
    public String section;
    @JsonProperty("position")
    public Position position;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.site == null)? 0 :this.site.hashCode()));
        result = ((result* 31)+((this.section == null)? 0 :this.section.hashCode()));
        result = ((result* 31)+((this.position == null)? 0 :this.position.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Location) == false) {
            return false;
        }
        Location rhs = ((Location) other);
        return ((((this.site == rhs.site)||((this.site!= null)&&this.site.equals(rhs.site)))&&((this.section == rhs.section)||((this.section!= null)&&this.section.equals(rhs.section))))&&((this.position == rhs.position)||((this.position!= null)&&this.position.equals(rhs.position))));
    }

}
