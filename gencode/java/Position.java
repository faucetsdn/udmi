import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "x",
    "y"
})
@Generated("jsonschema2pojo")
public class Position {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("x")
    private Double x;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("y")
    private Double y;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("x")
    public Double getX() {
        return x;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("x")
    public void setX(Double x) {
        this.x = x;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("y")
    public Double getY() {
        return y;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("y")
    public void setY(Double y) {
        this.y = y;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Position.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("x");
        sb.append('=');
        sb.append(((this.x == null)?"<null>":this.x));
        sb.append(',');
        sb.append("y");
        sb.append('=');
        sb.append(((this.y == null)?"<null>":this.y));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.x == null)? 0 :this.x.hashCode()));
        result = ((result* 31)+((this.y == null)? 0 :this.y.hashCode()));
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
        return (((this.x == rhs.x)||((this.x!= null)&&this.x.equals(rhs.x)))&&((this.y == rhs.y)||((this.y!= null)&&this.y.equals(rhs.y))));
    }

}
