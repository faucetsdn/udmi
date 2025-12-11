package webapp;

import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static webapp.ManagerServlet.logMessage;

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

    // region Json Helpers
    private JsonObject getJsonObject(String key, JsonObject parent) {

        if (key != null && parent != null && parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private JsonArray getJsonArray(String key, JsonObject parent) {
        if (key != null && parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
            return parent.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private String getString(String key, JsonObject parent){
        if(key != null && parent != null && parent.has(key)){
            try{
                return parent.get(key).getAsString();
            } catch (Exception ignore) {}
        }
        return null;
    }
    // endregion Json Helpers

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

        List<String> matchingKeys = devices.keySet().stream()
                .filter(key -> key.toLowerCase().contains(deviceName.toLowerCase()))
                .limit(100)
                .toList();

        StringBuilder stringBuilder = new StringBuilder();

        String statusFormat = """
            <tr>
                <td>%s</td>
                <td>%s</td>
                <td class="badge-%s">%s</td>
            </tr>
            """;

        for (String name : matchingKeys){
            JsonObject deviceReport = getJsonObject(name, devices);
            JsonObject status = getJsonObject("status", deviceReport);

            // Format date
            String lastSeen = getFormattedTimestamp(getString("last_seen", deviceReport));

            // Get device status
            String colour = getDeviceColourState(name);

            // Get status message
            String message = getString("message", status);

            stringBuilder.append(String.format(statusFormat,
                    name,
                    lastSeen,
                    colour,
                    message
            ));
        }
        return stringBuilder.toString();
    }

    public String getDeviceStatus() {
        return getDeviceStatus(null);
    }

    public String getDeviceStatus(String deviceName) {
        StringBuilder tableContents = new StringBuilder();

        if (devices != null) {
            if (deviceName != null && !deviceName.isEmpty() && !deviceName.isBlank()) {
                tableContents.append(getHTMLDeviceStatus(deviceName));
            } else {
                Iterator<String> names = devices.keySet().iterator();
                int i = 0;
                while (names.hasNext() && i <= 100) { // max display number
                    i++;
                    tableContents.append(getHTMLDeviceStatus(names.next()));
                }
            }
        }

        if(tableContents.isEmpty()){
            return """
                    <tr>
                        <td colspan="3">
                            <div id="empty-table-message">
                                No results to show
                            </div>
                        </td>
                    </tr>
                    """;
        }
        return tableContents.toString();
    }

    public String getLastRegistrarRun(){
        if (registrarSummary == null || registrarSummary.isEmpty()){
            try (Reader reader = new FileReader("/root/site_model/out/registration_summary.json")){
                JsonElement jsonElement = JsonParser.parseReader(reader);
                registrarSummary = jsonElement.getAsJsonObject();
            } catch (Exception e) {
                logMessage("Error getting registrar summary: " + e.getMessage());
                return "";
            }
        }

        return getFormattedTimestamp(getString("timestamp", registrarSummary));
    }

    public String getValidatorStatus(){
        return NOT_STARTED;
    }

    public String getDevices(){
        StringBuilder deviceOptions = new StringBuilder();
        Path startPath = Paths.get("/root/site_model/devices");
        try (var stream = Files.list(startPath)) {
            String htmlContent = stream
                    .filter(Files::isDirectory)
                    .map(path -> {
                        String name = path.getFileName().toString();
                        String fullPath = path.toString();
                        return String.format(
                                "<tr><td class=\"small\"><button class=\"small-button get-metadata\"  data-path=\"%s\">%s</button></td></tr>",
                                fullPath,
                                name
                        );
                    })
                    .collect(Collectors.joining("\n"));
            deviceOptions.append(htmlContent);
        } catch (IOException e) {
            logMessage("Error getting device list: " + e.getMessage());
        }
        return deviceOptions.toString();
    }

    public JsonObject getDeviceMetadata(String path){
        String metadataPath = path + File.separator+ "metadata.json";
        String error;
        try(Reader reader = new FileReader(metadataPath)){
            JsonElement jsonElement = JsonParser.parseReader(reader);
            return jsonElement.getAsJsonObject();
        } catch (IOException e) {
            error = "Error getting metadata file for given path: " + path + ", Error: " + e.getMessage();
            logMessage(error);
        } catch (Exception e){
            error = "Error parsing metadata file for given path: " + path + ", Error: " + e.getMessage();
            logMessage(error);
        }
        JsonObject returnObject = new JsonObject();
        returnObject.addProperty("Error", error);
        returnObject.addProperty("Message", "You can use this space to save a new json at path: "+ metadataPath);
        return returnObject;
    }

    private String getFormattedTimestamp(String ISOString){
        String formattedLastSeen;
        if (ISOString == null || ISOString.isEmpty()){
            formattedLastSeen = " --- ";
        }else{
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss, dd MMM yyyy");
                Instant instant = Instant.parse(ISOString);
                formattedLastSeen = sdf.format(new Date(instant.toEpochMilli()));
            } catch (Exception ignore) {
                formattedLastSeen = " --- ";
            }
        }
        return formattedLastSeen;
    }

    /********************************************************************************/

    JsonObject validationReport;
    JsonObject devices;
    JsonObject registrarSummary;
    JsonArray correctDevices;
    JsonArray missingDevices;
    JsonArray devicesInError;
    JsonArray extraDevices;

    public static final String RUNNING = "Running";
    public static final String NOT_STARTED = "Not Started";
}
