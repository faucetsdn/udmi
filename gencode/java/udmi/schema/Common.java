
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Common
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "family"
})
public class Common {

    /**
     * Protocol Family
     * <p>
     * 
     * 
     */
    @JsonProperty("family")
    public String family;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.family == null)? 0 :this.family.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Common) == false) {
            return false;
        }
        Common rhs = ((Common) other);
        return ((this.family == rhs.family)||((this.family!= null)&&this.family.equals(rhs.family)));
    }

}
