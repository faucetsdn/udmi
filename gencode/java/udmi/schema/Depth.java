
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Depth
 * <p>
 * 
 * 
 */
public enum Depth {

    CONTAINERS(null),
    ENTRIES(null),
    DETAILS(null),
    PARTS(null);
    private final Object value;
    private final static Map<Object, Depth> CONSTANTS = new HashMap<Object, Depth>();

    static {
        for (Depth c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    Depth(Object value) {
        this.value = value;
    }

    @JsonValue
    public Object value() {
        return this.value;
    }

    @JsonCreator
    public static Depth fromValue(Object value) {
        Depth constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException((value +""));
        } else {
            return constant;
        }
    }

}
