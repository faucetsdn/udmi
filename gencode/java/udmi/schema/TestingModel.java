
package udmi.schema;

import java.util.HashMap;
import java.util.List;
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
    "nostate",
    "targets",
    "ota_targets"
})
public class TestingModel {

    @JsonProperty("nostate")
    public Boolean nostate;
    @JsonProperty("targets")
    public HashMap<String, TargetTestingModel> targets;
    @JsonProperty("ota_targets")
    public List<OtaTestingModel> ota_targets;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.targets == null)? 0 :this.targets.hashCode()));
        result = ((result* 31)+((this.ota_targets == null)? 0 :this.ota_targets.hashCode()));
        result = ((result* 31)+((this.nostate == null)? 0 :this.nostate.hashCode()));
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
        return ((((this.targets == rhs.targets)||((this.targets!= null)&&this.targets.equals(rhs.targets)))&&((this.ota_targets == rhs.ota_targets)||((this.ota_targets!= null)&&this.ota_targets.equals(rhs.ota_targets))))&&((this.nostate == rhs.nostate)||((this.nostate!= null)&&this.nostate.equals(rhs.nostate))));
    }

}
