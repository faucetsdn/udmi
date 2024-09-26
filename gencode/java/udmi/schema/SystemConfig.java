
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System Config
 * <p>
 * [System Config Documentation](../docs/messages/system.md#config)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "min_loglevel",
    "metrics_rate_sec",
    "operation",
    "testing"
})
public class SystemConfig {

    /**
     * The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.
     * 
     */
    @JsonProperty("min_loglevel")
    @JsonPropertyDescription("The minimum loglevel for reporting log messages below which log entries should not be sent. Default to 300.")
    public Integer min_loglevel = 300;
    /**
     * The rate at which the system should send system event updates. 0 indicates no updates.
     * 
     */
    @JsonProperty("metrics_rate_sec")
    @JsonPropertyDescription("The rate at which the system should send system event updates. 0 indicates no updates.")
    public Integer metrics_rate_sec = 10;
    @JsonProperty("operation")
    public Operation operation;
    /**
     * Testing System Config
     * <p>
     * Configuration parameters for device-under-test
     * 
     */
    @JsonProperty("testing")
    @JsonPropertyDescription("Configuration parameters for device-under-test")
    public TestingSystemConfig testing;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.metrics_rate_sec == null)? 0 :this.metrics_rate_sec.hashCode()));
        result = ((result* 31)+((this.operation == null)? 0 :this.operation.hashCode()));
        result = ((result* 31)+((this.min_loglevel == null)? 0 :this.min_loglevel.hashCode()));
        result = ((result* 31)+((this.testing == null)? 0 :this.testing.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SystemConfig) == false) {
            return false;
        }
        SystemConfig rhs = ((SystemConfig) other);
        return (((((this.metrics_rate_sec == rhs.metrics_rate_sec)||((this.metrics_rate_sec!= null)&&this.metrics_rate_sec.equals(rhs.metrics_rate_sec)))&&((this.operation == rhs.operation)||((this.operation!= null)&&this.operation.equals(rhs.operation))))&&((this.min_loglevel == rhs.min_loglevel)||((this.min_loglevel!= null)&&this.min_loglevel.equals(rhs.min_loglevel))))&&((this.testing == rhs.testing)||((this.testing!= null)&&this.testing.equals(rhs.testing))));
    }

}
