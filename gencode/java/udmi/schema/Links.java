
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "docs",
    "folder",
    "image",
    "repo"
})
@Generated("jsonschema2pojo")
public class Links {

    /**
     * Folder where the project documentation is stored
     * 
     */
    @JsonProperty("docs")
    @JsonPropertyDescription("Folder where the project documentation is stored")
    public String docs;
    /**
     * Folder where the UDMI site model is stored
     * 
     */
    @JsonProperty("folder")
    @JsonPropertyDescription("Folder where the UDMI site model is stored")
    public String folder;
    /**
     * Image of the site
     * 
     */
    @JsonProperty("image")
    @JsonPropertyDescription("Image of the site")
    public String image;
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
        result = ((result* 31)+((this.image == null)? 0 :this.image.hashCode()));
        result = ((result* 31)+((this.folder == null)? 0 :this.folder.hashCode()));
        result = ((result* 31)+((this.docs == null)? 0 :this.docs.hashCode()));
        result = ((result* 31)+((this.repo == null)? 0 :this.repo.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Links) == false) {
            return false;
        }
        Links rhs = ((Links) other);
        return (((((this.image == rhs.image)||((this.image!= null)&&this.image.equals(rhs.image)))&&((this.folder == rhs.folder)||((this.folder!= null)&&this.folder.equals(rhs.folder))))&&((this.docs == rhs.docs)||((this.docs!= null)&&this.docs.equals(rhs.docs))))&&((this.repo == rhs.repo)||((this.repo!= null)&&this.repo.equals(rhs.repo))));
    }

}
