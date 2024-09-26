
package udmi.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Properties of the expected physical location of the device
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "site",
    "panel",
    "section",
    "room",
    "floor",
    "floor_seq",
    "position",
    "coordinates"
})
public class Location {

    /**
     * The site name according to the site model in which the device is installed in
     * (Required)
     * 
     */
    @JsonProperty("site")
    @JsonPropertyDescription("The site name according to the site model in which the device is installed in")
    public String site;
    /**
     * The reference of the panel where the device is installed in
     * 
     */
    @JsonProperty("panel")
    @JsonPropertyDescription("The reference of the panel where the device is installed in")
    public String panel;
    @JsonProperty("section")
    public String section;
    @JsonProperty("room")
    public String room;
    /**
     * Name of floor level
     * 
     */
    @JsonProperty("floor")
    @JsonPropertyDescription("Name of floor level")
    public String floor;
    /**
     * Sequential integer representation for a floor, primarily for comparisons when non integer floors are used, e.g. 1 and 1M
     * 
     */
    @JsonProperty("floor_seq")
    @JsonPropertyDescription("Sequential integer representation for a floor, primarily for comparisons when non integer floors are used, e.g. 1 and 1M")
    public Integer floor_seq;
    @JsonProperty("position")
    public Position position;
    @JsonProperty("coordinates")
    public Coordinates coordinates;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.site == null)? 0 :this.site.hashCode()));
        result = ((result* 31)+((this.floor_seq == null)? 0 :this.floor_seq.hashCode()));
        result = ((result* 31)+((this.coordinates == null)? 0 :this.coordinates.hashCode()));
        result = ((result* 31)+((this.section == null)? 0 :this.section.hashCode()));
        result = ((result* 31)+((this.position == null)? 0 :this.position.hashCode()));
        result = ((result* 31)+((this.panel == null)? 0 :this.panel.hashCode()));
        result = ((result* 31)+((this.floor == null)? 0 :this.floor.hashCode()));
        result = ((result* 31)+((this.room == null)? 0 :this.room.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Location) == false) {
            return false;
        }
        Location rhs = ((Location) other);
        return (((((((((this.site == rhs.site)||((this.site!= null)&&this.site.equals(rhs.site)))&&((this.floor_seq == rhs.floor_seq)||((this.floor_seq!= null)&&this.floor_seq.equals(rhs.floor_seq))))&&((this.coordinates == rhs.coordinates)||((this.coordinates!= null)&&this.coordinates.equals(rhs.coordinates))))&&((this.section == rhs.section)||((this.section!= null)&&this.section.equals(rhs.section))))&&((this.position == rhs.position)||((this.position!= null)&&this.position.equals(rhs.position))))&&((this.panel == rhs.panel)||((this.panel!= null)&&this.panel.equals(rhs.panel))))&&((this.floor == rhs.floor)||((this.floor!= null)&&this.floor.equals(rhs.floor))))&&((this.room == rhs.room)||((this.room!= null)&&this.room.equals(rhs.room))));
    }

}
