
package udmi.schema;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Point Enumeration Event
 * <p>
 * Object representation for for a single point enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "possible_values",
    "units",
    "type",
    "ref",
    "writable",
    "description",
    "status",
    "ancillary"
})
@Generated("jsonschema2pojo")
public class PointEnumerationEvent {

    /**
     * List of possible enumerated values for the point
     * 
     */
    @JsonProperty("possible_values")
    @JsonPropertyDescription("List of possible enumerated values for the point")
    public List<String> possible_values = new ArrayList<String>();
    /**
     * Current or default unit for this point
     * 
     */
    @JsonProperty("units")
    @JsonPropertyDescription("Current or default unit for this point")
    public String units;
    /**
     * Current or default type for this point
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("Current or default type for this point")
    public String type;
    /**
     * Reference parameter for this point (e.g. BACnet object)
     * 
     */
    @JsonProperty("ref")
    @JsonPropertyDescription("Reference parameter for this point (e.g. BACnet object)")
    public String ref;
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
    public String description;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;
    /**
     * Arbitrary blob of json associated with this point
     * 
     */
    @JsonProperty("ancillary")
    @JsonPropertyDescription("Arbitrary blob of json associated with this point")
    public Ancillary__2 ancillary;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.possible_values == null)? 0 :this.possible_values.hashCode()));
        result = ((result* 31)+((this.description == null)? 0 :this.description.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.type == null)? 0 :this.type.hashCode()));
        result = ((result* 31)+((this.ancillary == null)? 0 :this.ancillary.hashCode()));
        result = ((result* 31)+((this.writable == null)? 0 :this.writable.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PointEnumerationEvent) == false) {
            return false;
        }
        PointEnumerationEvent rhs = ((PointEnumerationEvent) other);
        return (((((((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.possible_values == rhs.possible_values)||((this.possible_values!= null)&&this.possible_values.equals(rhs.possible_values))))&&((this.description == rhs.description)||((this.description!= null)&&this.description.equals(rhs.description))))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.type == rhs.type)||((this.type!= null)&&this.type.equals(rhs.type))))&&((this.ancillary == rhs.ancillary)||((this.ancillary!= null)&&this.ancillary.equals(rhs.ancillary))))&&((this.writable == rhs.writable)||((this.writable!= null)&&this.writable.equals(rhs.writable))))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }

}
