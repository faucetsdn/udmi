
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Monitoring
 * <p>
 * Output from UDMIS monitoring
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "metric"
})
public class Monitoring {

    /**
     * Monitoring metric
     * <p>
     * One metric
     * 
     */
    @JsonProperty("metric")
    @JsonPropertyDescription("One metric")
    public MonitoringMetric metric;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.metric == null)? 0 :this.metric.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Monitoring) == false) {
            return false;
        }
        Monitoring rhs = ((Monitoring) other);
        return ((this.metric == rhs.metric)||((this.metric!= null)&&this.metric.equals(rhs.metric)));
    }

}
