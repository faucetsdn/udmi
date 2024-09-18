
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Ref Discovery
 * <p>
 * Object representation for for a single point reference discovery
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "point",
    "name",
    "possible_values",
    "units",
    "type",
    "writable",
    "description",
    "status",
    "ancillary"
})
public class RefDiscovery {

    /**
     * Point descriptor for this point
     * 
     */
    @JsonProperty("point")
    @JsonPropertyDescription("Point descriptor for this point")
    public java.lang.String point;
    /**
     * Friendly name for the point, if known
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Friendly name for the point, if known")
    public java.lang.String name;
    /**
     * List of possible enumerated values for the point
     * 
     */
    @JsonProperty("possible_values")
    @JsonPropertyDescription("List of possible enumerated values for the point")
    public List<java.lang.String> possible_values = new ArrayList<java.lang.String>();
    /**
     * Current or default unit for this point
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("Current or default unit for this point")
    public java.lang.String units;
    /**
     * Current or default type for this point
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Current or default type for this point")
    public java.lang.String type;
    /**
     * Indicates if this point is writable or not
     * 
     */
    @JsonProperty("writable")
    @JsonPropertyDescription("Indicates if this point is writable or not")
    public Boolean writable;
    /**
     * Human-readable description of this point
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("Human-readable description of this point")
    public java.lang.String description;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    /**
     * Ancillary Properties
     * <p>
     * Arbitrary blob of json associated with this point
     * 
     */
    @JsonProperty("ancillary")
    @JsonPropertyDescription("Arbitrary blob of json associated with this point")
    public Map<String, Object> ancillary;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.possible_values == null)? 0 :this.possible_values.hashCode()));
        result = ((result* 31)+((this.name == null)? 0 :this.name.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.ancillary == null)? 0 :this.ancillary.hashCode()));
        result = ((result* 31)+((this.point == null)? 0 :this.point.hashCode()));
        result = ((result* 31)+((this.writable == null)? 0 :this.writable.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(java.lang.Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof RefDiscovery) == false) {
            return false;
        }
        RefDiscovery rhs = ((RefDiscovery) other);
        return ((((((((((this.possible_values == rhs.possible_values)||((this.possible_values!= null)&&this.possible_values.equals(rhs.possible_values)))&&((this.name == rhs.name)||((this.name!= null)&&this.name.equals(rhs.name))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.ancillary == rhs.ancillary)||((this.ancillary!= null)&&this.ancillary.equals(rhs.ancillary))))&&((this.point == rhs.point)||((this.point!= null)&&this.point.equals(rhs.point))))&&((this.writable == rhs.writable)||((this.writable!= null)&&this.writable.equals(rhs.writable))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
