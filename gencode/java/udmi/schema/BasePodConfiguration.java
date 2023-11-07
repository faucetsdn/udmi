
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Base Pod Configuration
 * <p>
 * Parameters to define pod base parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "udmi_prefix"
})
@Generated("jsonschema2pojo")
public class BasePodConfiguration {

    /**
     * prefix for udmi namespacing
     * 
     */
    @JsonProperty("udmi_prefix")
    @JsonPropertyDescription("prefix for udmi namespacing")
    public String udmi_prefix;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.udmi_prefix == null)? 0 :this.udmi_prefix.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BasePodConfiguration) == false) {
            return false;
        }
        BasePodConfiguration rhs = ((BasePodConfiguration) other);
        return ((this.udmi_prefix == rhs.udmi_prefix)||((this.udmi_prefix!= null)&&this.udmi_prefix.equals(rhs.udmi_prefix)));
    }

}
