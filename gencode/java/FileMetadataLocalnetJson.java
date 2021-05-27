import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Local network metadata snippet
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subsystem"
})
@Generated("jsonschema2pojo")
public class FileMetadataLocalnetJson {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subsystem")
    private Subsystem__2 subsystem;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subsystem")
    public Subsystem__2 getSubsystem() {
        return subsystem;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("subsystem")
    public void setSubsystem(Subsystem__2 subsystem) {
        this.subsystem = subsystem;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(FileMetadataLocalnetJson.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("subsystem");
        sb.append('=');
        sb.append(((this.subsystem == null)?"<null>":this.subsystem));
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
        result = ((result* 31)+((this.subsystem == null)? 0 :this.subsystem.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof FileMetadataLocalnetJson) == false) {
            return false;
        }
        FileMetadataLocalnetJson rhs = ((FileMetadataLocalnetJson) other);
        return ((this.subsystem == rhs.subsystem)||((this.subsystem!= null)&&this.subsystem.equals(rhs.subsystem)));
    }

}
