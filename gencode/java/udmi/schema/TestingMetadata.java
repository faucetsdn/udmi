
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Testing Metadata
 * <p>
 * Testing targets are used
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "targets"
})
@Generated("jsonschema2pojo")
public class TestingMetadata {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("targets")
    public Targets targets;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.targets == null)? 0 :this.targets.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TestingMetadata) == false) {
            return false;
        }
        TestingMetadata rhs = ((TestingMetadata) other);
        return ((this.targets == rhs.targets)||((this.targets!= null)&&this.targets.equals(rhs.targets)));
    }

}
