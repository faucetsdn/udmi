
package udmi.schema;

import java.util.Date;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Monitoring metric
 * <p>
 * One metric
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "timestamp",
    "severity",
    "project",
    "location",
    "registry",
    "device_id",
    "device_num",
    "status_message"
})
@Generated("jsonschema2pojo")
public class MonitoringMetric {

    @JsonProperty("timestamp")
    public Date timestamp;
    @JsonProperty("severity")
    public String severity;
    @JsonProperty("project")
    public String project;
    @JsonProperty("location")
    public String location;
    @JsonProperty("registry")
    public String registry;
    @JsonProperty("device_id")
    public String device_id;
    @JsonProperty("device_num")
    public String device_num;
    @JsonProperty("status_message")
    public String status_message;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.severity == null)? 0 :this.severity.hashCode()));
        result = ((result* 31)+((this.status_message == null)? 0 :this.status_message.hashCode()));
        result = ((result* 31)+((this.registry == null)? 0 :this.registry.hashCode()));
        result = ((result* 31)+((this.device_id == null)? 0 :this.device_id.hashCode()));
        result = ((result* 31)+((this.project == null)? 0 :this.project.hashCode()));
        result = ((result* 31)+((this.device_num == null)? 0 :this.device_num.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.timestamp == null)? 0 :this.timestamp.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MonitoringMetric) == false) {
            return false;
        }
        MonitoringMetric rhs = ((MonitoringMetric) other);
        return (((((((((this.severity == rhs.severity)||((this.severity!= null)&&this.severity.equals(rhs.severity)))&&((this.status_message == rhs.status_message)||((this.status_message!= null)&&this.status_message.equals(rhs.status_message))))&&((this.registry == rhs.registry)||((this.registry!= null)&&this.registry.equals(rhs.registry))))&&((this.device_id == rhs.device_id)||((this.device_id!= null)&&this.device_id.equals(rhs.device_id))))&&((this.project == rhs.project)||((this.project!= null)&&this.project.equals(rhs.project))))&&((this.device_num == rhs.device_num)||((this.device_num!= null)&&this.device_num.equals(rhs.device_num))))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.timestamp == rhs.timestamp)||((this.timestamp!= null)&&this.timestamp.equals(rhs.timestamp))));
    }

}
