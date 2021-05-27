import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * System metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "location",
    "physical_tag",
    "aux"
})
@Generated("jsonschema2pojo")
public class MetadataSystem {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("location")
    private Location__1 location;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    private PhysicalTag__1 physicalTag;
    @JsonProperty("aux")
    private Aux__1 aux;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("location")
    public Location__1 getLocation() {
        return location;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("location")
    public void setLocation(Location__1 location) {
        this.location = location;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    public PhysicalTag__1 getPhysicalTag() {
        return physicalTag;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("physical_tag")
    public void setPhysicalTag(PhysicalTag__1 physicalTag) {
        this.physicalTag = physicalTag;
    }

    @JsonProperty("aux")
    public Aux__1 getAux() {
        return aux;
    }

    @JsonProperty("aux")
    public void setAux(Aux__1 aux) {
        this.aux = aux;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSystem.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("location");
        sb.append('=');
        sb.append(((this.location == null)?"<null>":this.location));
        sb.append(',');
        sb.append("physicalTag");
        sb.append('=');
        sb.append(((this.physicalTag == null)?"<null>":this.physicalTag));
        sb.append(',');
        sb.append("aux");
        sb.append('=');
        sb.append(((this.aux == null)?"<null>":this.aux));
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
        result = ((result* 31)+((this.physicalTag == null)? 0 :this.physicalTag.hashCode()));
        result = ((result* 31)+((this.location == null)? 0 :this.location.hashCode()));
        result = ((result* 31)+((this.aux == null)? 0 :this.aux.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof MetadataSystem) == false) {
            return false;
        }
        MetadataSystem rhs = ((MetadataSystem) other);
        return ((((this.physicalTag == rhs.physicalTag)||((this.physicalTag!= null)&&this.physicalTag.equals(rhs.physicalTag)))&&((this.location == rhs.location)||((this.location!= null)&&this.location.equals(rhs.location))))&&((this.aux == rhs.aux)||((this.aux!= null)&&this.aux.equals(rhs.aux))));
    }

}
