package webapp;

import com.google.gson.*;

import java.io.FileReader;
import java.io.Reader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Iterator;

import static webapp.ManagerServlet.logMessage;
import static webapp.ManagerWebsocket.sendWebsocketMessage;

public class UDMISManager {

    public UDMISManager() {
        try (Reader reader = new FileReader("/root/site_model/out/validation_report.json")) {
            JsonElement jsonElement = JsonParser.parseReader(reader);

            validationReport = jsonElement.getAsJsonObject();

            devices = getJsonObject("devices", validationReport);

            JsonObject summary = getJsonObject("summary", validationReport);
            correctDevices = getJsonArray("correct_devices", summary);
            missingDevices = getJsonArray("missing_devices", summary);
            devicesInError = getJsonArray("error_devices", summary);
            extraDevices = getJsonArray("extra_devices", summary);

        } catch (Exception e) {
            logMessage("Error setting up udmis manager object: " + e.getMessage());
        }
    }

    private JsonObject getJsonObject(String objectKey, JsonObject parent) {
        if (parent.has(objectKey) && parent.get(objectKey).isJsonObject()) {
            return parent.getAsJsonObject(objectKey);
        }
        return new JsonObject();
    }

    private JsonArray getJsonArray(String objectKey, JsonObject parent) {
        if (parent.has(objectKey) && parent.get(objectKey).isJsonArray()) {
            return parent.getAsJsonArray(objectKey);
        }
        return new JsonArray();
    }

    private String getDeviceColourState(String deviceName) {
        JsonElement name = new JsonPrimitive(deviceName);
        if (correctDevices.contains(name)) {
            return "green";
        } else if (devicesInError.contains(name)) {
            return "red";
        } else if (missingDevices.contains(name)) {
            return "yellow";
        } else {
            return "null";
        }
    }

    private String getHTMLDeviceStatus(String deviceName) {
        String statusFormat = """
                <tr>
                    <td>%s</td>
                    <td>%s</td>
                    <td class="badge-%s">%s</td>
                </tr>
                """;


        JsonObject status = devices.getAsJsonObject(deviceName);

        // Format date
        Instant instant = Instant.parse(status.get("last_seen").getAsString());
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault());
        String lastSeen = formatter.format(instant);

        // Get device status
        String colour = getDeviceColourState(deviceName);

        // Get status message
        String message = status.getAsJsonObject("status").get("message").getAsString();

        return String.format(statusFormat,
                deviceName,
                lastSeen,
                colour,
                message
        );
    }

    public void getDeviceStatus() {
        getDeviceStatus(null);
    }

    public void getDeviceStatus(String deviceName) {
        if (devices == null) {
            return;
        }

        StringBuilder tableContents = new StringBuilder();

        if (deviceName != null) {
            tableContents.append(getHTMLDeviceStatus(deviceName));
        } else {
            Iterator<String> names = devices.keySet().iterator();
            int i = 0;
            while (names.hasNext() && i <= 100) { // max display number
                i++;
                tableContents.append(getHTMLDeviceStatus(names.next()));
            }
        }

        sendWebsocketMessage(DEVICE_STATUS, tableContents.toString());
    }

    /********************************************************************************/

    JsonObject validationReport;
    JsonObject devices;
    JsonArray correctDevices;
    JsonArray missingDevices;
    JsonArray devicesInError;
    JsonArray extraDevices;

    /* message keys */
    private static final String DEVICE_STATUS = "deviceStatus";
}
