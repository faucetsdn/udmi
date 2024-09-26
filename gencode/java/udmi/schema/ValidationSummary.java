
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
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
    "correct_devices",
    "extra_devices",
    "missing_devices",
    "error_devices"
})
public class ValidationSummary {

    @JsonProperty("correct_devices")
    public List<String> correct_devices = new ArrayList<String>();
    @JsonProperty("extra_devices")
    public List<String> extra_devices = new ArrayList<String>();
    @JsonProperty("missing_devices")
    public List<String> missing_devices = new ArrayList<String>();
    @JsonProperty("error_devices")
    public List<String> error_devices = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.error_devices == null)? 0 :this.error_devices.hashCode()));
        result = ((result* 31)+((this.correct_devices == null)? 0 :this.correct_devices.hashCode()));
        result = ((result* 31)+((this.extra_devices == null)? 0 :this.extra_devices.hashCode()));
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
        return (((((this.error_devices == rhs.error_devices)||((this.error_devices!= null)&&this.error_devices.equals(rhs.error_devices)))&&((this.correct_devices == rhs.correct_devices)||((this.correct_devices!= null)&&this.correct_devices.equals(rhs.correct_devices))))&&((this.extra_devices == rhs.extra_devices)||((this.extra_devices!= null)&&this.extra_devices.equals(rhs.extra_devices))))&&((this.missing_devices == rhs.missing_devices)||((this.missing_devices!= null)&&this.missing_devices.equals(rhs.missing_devices))));
    }

}
