
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Feature Enumeration Event
 * <p>
 * Object representation for for a single feature enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "networks"
})
@Generated("jsonschema2pojo")
public class FeatureEnumerationEvent {

    /**
     * Collection of networks that this feature applies to
     * 
     */
    @JsonProperty("networks")
    @JsonPropertyDescription("Collection of networks that this feature applies to")
    public Networks networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FeatureEnumerationEvent) == false) {
            return false;
        }
        FeatureEnumerationEvent rhs = ((FeatureEnumerationEvent) other);
        return ((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks)));
    }

}
