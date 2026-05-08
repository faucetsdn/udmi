package webapp.mqtt;

import com.google.gson.*;
import webapp.ValidatorManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static webapp.ManagerServlet.getEmptyTableRow;
import static webapp.ManagerServlet.isValidString;

public class SiteModelManager {

    public String getDevices(String deviceName) {
        StringBuilder deviceOptions = new StringBuilder();
        Path startPath = Paths.get("/root/site_model/devices");
        try (var stream = Files.list(startPath)) {
            String htmlContent = stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        if (isValidString(deviceName) && !name.toLowerCase().contains(deviceName.toLowerCase())) {
                            return false;
                        }
                        return true;
                    }).map(path -> {
                        String fullPath = path.toString();
                        String name = path.getFileName().toString();
                        return String.format(
                                "<tr><td class=\"small\"><button class=\"small-button get-metadata\"  data-path=\"%s\">%s</button></td></tr>",
                                fullPath,
                                name);
                    })
                    .collect(Collectors.joining("\n"));
            deviceOptions.append(htmlContent);
        } catch (IOException e) {
            LOGGER.severe("Error getting device list: " + e.getMessage());
        }

        if (deviceOptions.isEmpty()) {
            return getEmptyTableRow();
        }
        return deviceOptions.toString();
    }

    /**********************************************************************/

    public JsonObject getDeviceMetadata(String path) {
        String metadataPath = path + File.separator + "metadata.json";
        String error;
        try (Reader reader = new FileReader(metadataPath)) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            return jsonElement.getAsJsonObject();
        } catch (IOException e) {
            error = "Error getting metadata file for given path: " + path + ", Error: " + e.getMessage();
            LOGGER.severe(error);
        } catch (Exception e) {
            error = "Error parsing metadata file for given path: " + path + ", Error: " + e.getMessage();
            LOGGER.severe(error);
        }
        JsonObject returnObject = new JsonObject();
        returnObject.addProperty("Error", error);
        returnObject.addProperty("Message", "You can use this space to save a new json at path: " + metadataPath);
        return returnObject;
    }

    /**********************************************************************/

    public JsonObject updateMetadata(String path, JsonObject metadata) {

        JsonObject reply = new JsonObject();
        reply.addProperty("message", "Saved Successfully!");

        try (PrintWriter writer = new PrintWriter(path + File.separator + "metadata.json", "UTF-8")) {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            gson.toJson(metadata, writer);
        } catch (IOException e) {
            String errorMsg = "Error saving metadata file: " + e.getMessage();
            reply.addProperty("message", errorMsg);
            reply.addProperty("messageType", "error");
            LOGGER.severe(errorMsg);
        }
        return reply;
    }

    /**********************************************************************/

    private final static Logger LOGGER = Logger.getLogger(SiteModelManager.class.getName());
}
