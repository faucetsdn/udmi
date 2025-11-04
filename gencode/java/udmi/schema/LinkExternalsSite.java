
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Link Externals Site
 * <p>
 * Information about how this site links to a specific external model
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "description"
})
public class LinkExternalsSite {

    /**
     * Description of this external link for this site
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Description of this external link for this site")
    public String description;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof LinkExternalsSite) == false) {
            return false;
        }
        LinkExternalsSite rhs = ((LinkExternalsSite) other);
        return ((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description)));
    }

}
