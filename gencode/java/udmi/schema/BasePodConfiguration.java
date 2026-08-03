
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Base Pod Configuration
 * <p>
 * Parameters to define pod base parameters
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasePodConfiguration {

    /**
     * prefix for udmi namespacing
     * 
     */
    @JsonProperty("udmi_prefix")
    @JsonPropertyDescription("prefix for udmi namespacing")
    public String udmi_prefix;
    /**
     * chance of random failure in various bits of the system
     * 
     */
    @JsonProperty("failure_rate")
    @JsonPropertyDescription("chance of random failure in various bits of the system")
    public Double failure_rate;
    /**
     * minimum log level for reporting log messages
     * 
     */
    @JsonProperty("log_level")
    @JsonPropertyDescription("minimum log level for reporting log messages")
    public String log_level;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.log_level == null)? 0 :this.log_level.hashCode()));
        result = ((result* 31)+((this.failure_rate == null)? 0 :this.failure_rate.hashCode()));
        result = ((result* 31)+((this.udmi_prefix == null)? 0 :this.udmi_prefix.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BasePodConfiguration) == false) {
            return false;
        }
        BasePodConfiguration rhs = ((BasePodConfiguration) other);
        return ((((this.log_level == rhs.log_level)||((this.log_level!= null)&&this.log_level.equals(rhs.log_level)))&&((this.failure_rate == rhs.failure_rate)||((this.failure_rate!= null)&&this.failure_rate.equals(rhs.failure_rate))))&&((this.udmi_prefix == rhs.udmi_prefix)||((this.udmi_prefix!= null)&&this.udmi_prefix.equals(rhs.udmi_prefix))));
    }

}
