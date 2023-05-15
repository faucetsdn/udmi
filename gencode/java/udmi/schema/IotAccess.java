
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Iot Access
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "provider",
    "project_id"
})
@Generated("jsonschema2pojo")
public class IotAccess {

    /**
     * Iot Provider
     * <p>
     * 
     * 
     */
    @JsonProperty("provider")
    public udmi.schema.ExecutionConfiguration.IotProvider provider;
    @JsonProperty("project_id")
    public String project_id;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.provider == null)? 0 :this.provider.hashCode()));
        result = ((result* 31)+((this.project_id == null)? 0 :this.project_id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof IotAccess) == false) {
            return false;
        }
        IotAccess rhs = ((IotAccess) other);
        return (((this.provider == rhs.provider)||((this.provider!= null)&&this.provider.equals(rhs.provider)))&&((this.project_id == rhs.project_id)||((this.project_id!= null)&&this.project_id.equals(rhs.project_id))));
    }

}
