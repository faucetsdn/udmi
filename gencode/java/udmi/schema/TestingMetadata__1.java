
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Testing Metadata
 * <p>
 * Testing target parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "targets"
})
@Generated("jsonschema2pojo")
public class TestingMetadata__1 {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("targets")
    public HashMap<String, TargetTestingMetadata> targets;

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
        if ((other instanceof TestingMetadata__1) == false) {
            return false;
        }
        TestingMetadata__1 rhs = ((TestingMetadata__1) other);
        return ((this.targets == rhs.targets)||((this.targets!= null)&&this.targets.equals(rhs.targets)));
    }

}
