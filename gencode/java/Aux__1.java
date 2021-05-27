import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "suffix"
})
@Generated("jsonschema2pojo")
public class Aux__1 {

    @JsonProperty("suffix")
    private String suffix;

    @JsonProperty("suffix")
    public String getSuffix() {
        return suffix;
    }

    @JsonProperty("suffix")
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Aux__1 .class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("suffix");
        sb.append('=');
        sb.append(((this.suffix == null)?"<null>":this.suffix));
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
        result = ((result* 31)+((this.suffix == null)? 0 :this.suffix.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Aux__1) == false) {
            return false;
        }
        Aux__1 rhs = ((Aux__1) other);
        return ((this.suffix == rhs.suffix)||((this.suffix!= null)&&this.suffix.equals(rhs.suffix)));
    }

}
