
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "modeled",
    "validated",
    "registered",
    "discovered"
})
@Generated("jsonschema2pojo")
public class Counts {

    /**
     * Count of modeled devices
     * 
     */
    @JsonProperty("modeled")
    @JsonPropertyDescription("Count of modeled devices")
    public Double modeled;
    /**
     * Count of validated devices
     * 
     */
    @JsonProperty("validated")
    @JsonPropertyDescription("Count of validated devices")
    public Double validated;
    /**
     * Count of registered devices
     * 
     */
    @JsonProperty("registered")
    @JsonPropertyDescription("Count of registered devices")
    public Double registered;
    /**
     * Count of discovered devices
     * 
     */
    @JsonProperty("discovered")
    @JsonPropertyDescription("Count of discovered devices")
    public Double discovered;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.modeled == null)? 0 :this.modeled.hashCode()));
        result = ((result* 31)+((this.registered == null)? 0 :this.registered.hashCode()));
        result = ((result* 31)+((this.discovered == null)? 0 :this.discovered.hashCode()));
        result = ((result* 31)+((this.validated == null)? 0 :this.validated.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Counts) == false) {
            return false;
        }
        Counts rhs = ((Counts) other);
        return (((((this.modeled == rhs.modeled)||((this.modeled!= null)&&this.modeled.equals(rhs.modeled)))&&((this.registered == rhs.registered)||((this.registered!= null)&&this.registered.equals(rhs.registered))))&&((this.discovered == rhs.discovered)||((this.discovered!= null)&&this.discovered.equals(rhs.discovered))))&&((this.validated == rhs.validated)||((this.validated!= null)&&this.validated.equals(rhs.validated))));
    }

}
