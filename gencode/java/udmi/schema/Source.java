
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "folder",
    "repo"
})
@Generated("jsonschema2pojo")
public class Source {

    /**
     * Folder where the UDMI site model is stored
     * 
     */
    @JsonProperty("folder")
    @JsonPropertyDescription("Folder where the UDMI site model is stored")
    public String folder;
    /**
     * Source repository where the UDMI site model is stored
     * 
     */
    @JsonProperty("repo")
    @JsonPropertyDescription("Source repository where the UDMI site model is stored")
    public String repo;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.folder == null)? 0 :this.folder.hashCode()));
        result = ((result* 31)+((this.repo == null)? 0 :this.repo.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Source) == false) {
            return false;
        }
        Source rhs = ((Source) other);
        return (((this.folder == rhs.folder)||((this.folder!= null)&&this.folder.equals(rhs.folder)))&&((this.repo == rhs.repo)||((this.repo!= null)&&this.repo.equals(rhs.repo))));
    }

}
