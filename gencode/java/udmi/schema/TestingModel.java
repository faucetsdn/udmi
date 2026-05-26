
package udmi.schema;

import java.util.HashMap;
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
    "blob_update_targets"
})
public class TestingModel {

    @JsonProperty("nostate")
    public Boolean nostate;
    @JsonProperty("targets")
    public HashMap<String, TargetTestingModel> targets;
    @JsonProperty("blob_update_targets")
    public HashMap<String, BlobUpdateTestingModel> blob_update_targets;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.blob_update_targets == null)? 0 :this.blob_update_targets.hashCode()));
        result = ((result* 31)+((this.targets == null)? 0 :this.targets.hashCode()));
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
        return ((((this.blob_update_targets == rhs.blob_update_targets)||((this.blob_update_targets!= null)&&this.blob_update_targets.equals(rhs.blob_update_targets)))&&((this.targets == rhs.targets)||((this.targets!= null)&&this.targets.equals(rhs.targets))))&&((this.nostate == rhs.nostate)||((this.nostate!= null)&&this.nostate.equals(rhs.nostate))));
    }

}
