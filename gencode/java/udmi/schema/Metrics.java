
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "restart_count",
    "mem_total_mb",
    "mem_free_mb",
    "cpu_load_5m"
})
@Generated("jsonschema2pojo")
public class Metrics {

    @JsonProperty("restart_count")
    public Double restart_count;
    @JsonProperty("mem_total_mb")
    public Double mem_total_mb;
    @JsonProperty("mem_free_mb")
    public Double mem_free_mb;
    @JsonProperty("cpu_load_5m")
    public Double cpu_load_5m;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.mem_free_mb == null)? 0 :this.mem_free_mb.hashCode()));
        result = ((result* 31)+((this.restart_count == null)? 0 :this.restart_count.hashCode()));
        result = ((result* 31)+((this.cpu_load_5m == null)? 0 :this.cpu_load_5m.hashCode()));
        result = ((result* 31)+((this.mem_total_mb == null)? 0 :this.mem_total_mb.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Metrics) == false) {
            return false;
        }
        Metrics rhs = ((Metrics) other);
        return (((((this.mem_free_mb == rhs.mem_free_mb)||((this.mem_free_mb!= null)&&this.mem_free_mb.equals(rhs.mem_free_mb)))&&((this.restart_count == rhs.restart_count)||((this.restart_count!= null)&&this.restart_count.equals(rhs.restart_count))))&&((this.cpu_load_5m == rhs.cpu_load_5m)||((this.cpu_load_5m!= null)&&this.cpu_load_5m.equals(rhs.cpu_load_5m))))&&((this.mem_total_mb == rhs.mem_total_mb)||((this.mem_total_mb!= null)&&this.mem_total_mb.equals(rhs.mem_total_mb))));
    }

}
