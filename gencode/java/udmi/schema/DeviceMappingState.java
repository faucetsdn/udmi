
package udmi.schema;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Device Mapping State
 * <p>
 * State for [mapping](../docs/specs/mapping.md)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "guid",
    "imported",
    "discovered",
    "predicted",
    "promoted",
    "exported",
    "status"
})
public class DeviceMappingState {

    /**
     * Device guid
     * 
     */
    @JsonProperty("guid")
    @JsonPropertyDescription("Device guid")
    public String guid;
    /**
     * Last time the mapping was imported
     * 
     */
    @JsonProperty("imported")
    @JsonPropertyDescription("Last time the mapping was imported")
    public Date imported;
    /**
     * Last received discovery message
     * 
     */
    @JsonProperty("discovered")
    @JsonPropertyDescription("Last received discovery message")
    public Date discovered;
    /**
     * Last recommendation staging (result of automatic prediction)
     * 
     */
    @JsonProperty("predicted")
    @JsonPropertyDescription("Last recommendation staging (result of automatic prediction)")
    public Date predicted;
    /**
     * Last recommendation promotion (result of manual QA)
     * 
     */
    @JsonProperty("promoted")
    @JsonPropertyDescription("Last recommendation promotion (result of manual QA)")
    public Date promoted;
    /**
     * Last time this device mapping was exported
     * 
     */
    @JsonProperty("exported")
    @JsonPropertyDescription("Last time this device mapping was exported")
    public Date exported;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.exported == null)? 0 :this.exported.hashCode()));
        result = ((result* 31)+((this.discovered == null)? 0 :this.discovered.hashCode()));
        result = ((result* 31)+((this.imported == null)? 0 :this.imported.hashCode()));
        result = ((result* 31)+((this.guid == null)? 0 :this.guid.hashCode()));
        result = ((result* 31)+((this.promoted == null)? 0 :this.promoted.hashCode()));
        result = ((result* 31)+((this.predicted == null)? 0 :this.predicted.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DeviceMappingState) == false) {
            return false;
        }
        DeviceMappingState rhs = ((DeviceMappingState) other);
        return ((((((((this.exported == rhs.exported)||((this.exported!= null)&&this.exported.equals(rhs.exported)))&&((this.discovered == rhs.discovered)||((this.discovered!= null)&&this.discovered.equals(rhs.discovered))))&&((this.imported == rhs.imported)||((this.imported!= null)&&this.imported.equals(rhs.imported))))&&((this.guid == rhs.guid)||((this.guid!= null)&&this.guid.equals(rhs.guid))))&&((this.promoted == rhs.promoted)||((this.promoted!= null)&&this.promoted.equals(rhs.promoted))))&&((this.predicted == rhs.predicted)||((this.predicted!= null)&&this.predicted.equals(rhs.predicted))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
