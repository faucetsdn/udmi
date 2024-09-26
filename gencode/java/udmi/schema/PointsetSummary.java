
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Pointset Summary
 * <p>
 * Errors specific to pointset handling
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "missing",
    "extra"
})
public class PointsetSummary {

    /**
     * Missing points discovered while validating a device
     * 
     */
    @JsonProperty("missing")
    @JsonPropertyDescription("Missing points discovered while validating a device")
    public List<String> missing = new ArrayList<String>();
    /**
     * Extra points discovered while validating a device
     * 
     */
    @JsonProperty("extra")
    @JsonPropertyDescription("Extra points discovered while validating a device")
    public List<String> extra = new ArrayList<String>();

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.missing == null)? 0 :this.missing.hashCode()));
        result = ((result* 31)+((this.extra == null)? 0 :this.extra.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointsetSummary) == false) {
            return false;
        }
        PointsetSummary rhs = ((PointsetSummary) other);
        return (((this.missing == rhs.missing)||((this.missing!= null)&&this.missing.equals(rhs.missing)))&&((this.extra == rhs.extra)||((this.extra!= null)&&this.extra.equals(rhs.extra))));
    }

}
