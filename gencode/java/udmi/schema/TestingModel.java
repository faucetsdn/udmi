
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Testing Model
 * <p>
 * Testing target parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "targets",
    "discovery"
})
@Generated("jsonschema2pojo")
public class TestingModel {

    @JsonProperty("targets")
    public HashMap<String, TargetTestingModel> targets;
    @JsonProperty("discovery")
    public Discovery discovery;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.targets == null)? 0 :this.targets.hashCode()));
        result = ((result* 31)+((this.discovery == null)? 0 :this.discovery.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof TestingModel) == false) {
            return false;
        }
        TestingModel rhs = ((TestingModel) other);
        return (((this.targets == rhs.targets)||((this.targets!= null)&&this.targets.equals(rhs.targets)))&&((this.discovery == rhs.discovery)||((this.discovery!= null)&&this.discovery.equals(rhs.discovery))));
    }

}
