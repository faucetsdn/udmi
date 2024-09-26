
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "mem_total_mb",
    "mem_free_mb",
    "store_total_mb",
    "store_free_mb",
    "system_load"
})
public class Metrics {

    @JsonProperty("mem_total_mb")
    public Double mem_total_mb;
    @JsonProperty("mem_free_mb")
    public Double mem_free_mb;
    @JsonProperty("store_total_mb")
    public Double store_total_mb;
    @JsonProperty("store_free_mb")
    public Double store_free_mb;
    @JsonProperty("system_load")
    public Double system_load;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.store_free_mb == null)? 0 :this.store_free_mb.hashCode()));
        result = ((result* 31)+((this.mem_free_mb == null)? 0 :this.mem_free_mb.hashCode()));
        result = ((result* 31)+((this.system_load == null)? 0 :this.system_load.hashCode()));
        result = ((result* 31)+((this.store_total_mb == null)? 0 :this.store_total_mb.hashCode()));
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
        return ((((((this.store_free_mb == rhs.store_free_mb)||((this.store_free_mb!= null)&&this.store_free_mb.equals(rhs.store_free_mb)))&&((this.mem_free_mb == rhs.mem_free_mb)||((this.mem_free_mb!= null)&&this.mem_free_mb.equals(rhs.mem_free_mb))))&&((this.system_load == rhs.system_load)||((this.system_load!= null)&&this.system_load.equals(rhs.system_load))))&&((this.store_total_mb == rhs.store_total_mb)||((this.store_total_mb!= null)&&this.store_total_mb.equals(rhs.store_total_mb))))&&((this.mem_total_mb == rhs.mem_total_mb)||((this.mem_total_mb!= null)&&this.mem_total_mb.equals(rhs.mem_total_mb))));
    }

}
