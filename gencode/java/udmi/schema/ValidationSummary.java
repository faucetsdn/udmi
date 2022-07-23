
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Validation Summary
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "extra_devices",
    "missing_devices",
    "pointset_devices",
    "base64_devices",
    "error_devices",
    "expected_devices"
})
@Generated("jsonschema2pojo")
public class ValidationSummary {

    @JsonProperty("extra_devices")
    public List<String> extra_devices = new ArrayList<String>();
    @JsonProperty("missing_devices")
    public List<String> missing_devices = new ArrayList<String>();
    @JsonProperty("pointset_devices")
    public List<String> pointset_devices = new ArrayList<String>();
    @JsonProperty("base64_devices")
    public List<String> base64_devices = new ArrayList<String>();
    @JsonProperty("error_devices")
    public List<String> error_devices = new ArrayList<String>();
    @JsonProperty("expected_devices")
    public List<String> expected_devices = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.expected_devices == null)? 0 :this.expected_devices.hashCode()));
        result = ((result* 31)+((this.pointset_devices == null)? 0 :this.pointset_devices.hashCode()));
        result = ((result* 31)+((this.error_devices == null)? 0 :this.error_devices.hashCode()));
        result = ((result* 31)+((this.extra_devices == null)? 0 :this.extra_devices.hashCode()));
        result = ((result* 31)+((this.base64_devices == null)? 0 :this.base64_devices.hashCode()));
        result = ((result* 31)+((this.missing_devices == null)? 0 :this.missing_devices.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ValidationSummary) == false) {
            return false;
        }
        ValidationSummary rhs = ((ValidationSummary) other);
        return (((((((this.expected_devices == rhs.expected_devices)||((this.expected_devices!= null)&&this.expected_devices.equals(rhs.expected_devices)))&&((this.pointset_devices == rhs.pointset_devices)||((this.pointset_devices!= null)&&this.pointset_devices.equals(rhs.pointset_devices))))&&((this.error_devices == rhs.error_devices)||((this.error_devices!= null)&&this.error_devices.equals(rhs.error_devices))))&&((this.extra_devices == rhs.extra_devices)||((this.extra_devices!= null)&&this.extra_devices.equals(rhs.extra_devices))))&&((this.base64_devices == rhs.base64_devices)||((this.base64_devices!= null)&&this.base64_devices.equals(rhs.base64_devices))))&&((this.missing_devices == rhs.missing_devices)||((this.missing_devices!= null)&&this.missing_devices.equals(rhs.missing_devices))));
    }

}
