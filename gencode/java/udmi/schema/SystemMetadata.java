
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Metadata
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "location",
    "physical_tag",
    "aux"
})
@Generated("jsonschema2pojo")
public class SystemMetadata {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("location")
    public Location location;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    public Physical_tag physical_tag;
    @JsonProperty("aux")
    public Aux aux;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.physical_tag == null)? 0 :this.physical_tag.hashCode()));
        result = ((result* 31)+((this.aux == null)? 0 :this.aux.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemMetadata) == false) {
            return false;
        }
        SystemMetadata rhs = ((SystemMetadata) other);
        return ((((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location)))&&((this.physical_tag == rhs.physical_tag)||((this.physical_tag!= null)&&this.physical_tag.equals(rhs.physical_tag))))&&((this.aux == rhs.aux)||((this.aux!= null)&&this.aux.equals(rhs.aux))));
    }

}
