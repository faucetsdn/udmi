
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "x",
    "y",
    "z"
})
public class Position {

    /**
     * The `x` coordinate of the device location in a project specific coordinate system
     * 
     */
    @JsonProperty("x")
    @JsonPropertyDescription("The `x` coordinate of the device location in a project specific coordinate system")
    public Double x;
    /**
     * The `y` coordinate of the device location in a project specific coordinate system
     * 
     */
    @JsonProperty("y")
    @JsonPropertyDescription("The `y` coordinate of the device location in a project specific coordinate system")
    public Double y;
    /**
     * The `z` (height) coordinate of the device location in a project specific coordinate system
     * 
     */
    @JsonProperty("z")
    @JsonPropertyDescription("The `z` (height) coordinate of the device location in a project specific coordinate system")
    public Double z;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.x == null)? 0 :this.x.hashCode()));
        result = ((result* 31)+((this.y == null)? 0 :this.y.hashCode()));
        result = ((result* 31)+((this.z == null)? 0 :this.z.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Position) == false) {
            return false;
        }
        Position rhs = ((Position) other);
        return ((((this.x == rhs.x)||((this.x!= null)&&this.x.equals(rhs.x)))&&((this.y == rhs.y)||((this.y!= null)&&this.y.equals(rhs.y))))&&((this.z == rhs.z)||((this.z!= null)&&this.z.equals(rhs.z))));
    }

}
