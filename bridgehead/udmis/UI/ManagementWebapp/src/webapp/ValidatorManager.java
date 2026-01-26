package webapp;

import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;
import static webapp.ManagerServlet.*;

public class ValidatorManager {

    public ValidatorManager() {
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
            LOGGER.severe("Error setting up udmis manager object: " + e.getMessage());
        }
    }

    /**********************************************************************/

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

    /**********************************************************************/

    private String getHTMLDeviceStatus(String deviceName){
        return getHTMLDeviceStatus(deviceName, false);
    }

    private String getHTMLDeviceStatus(String deviceName, boolean exactMatch) {

        List<String> matchingKeys =
                exactMatch
                        ? devices.keySet().stream()
                        .filter(key -> key.equals(deviceName))
                        .limit(100)
                        .toList()

                        : devices.keySet().stream()
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

        for (String name : matchingKeys) {
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
                    message));
        }
        return stringBuilder.toString();
    }

    /**********************************************************************/

    public String getDeviceStatus() {
        return getDeviceStatus(null);
    }

    public String getDeviceStatus(String deviceName) {
        StringBuilder tableContents = new StringBuilder();

        if (devices != null) {
            if (isValidString(deviceName)) {
                tableContents.append(getHTMLDeviceStatus(deviceName));
            } else {
                Iterator<String> names = devices.keySet().iterator();
                int i = 0;
                while (names.hasNext() && i <= 100) { // max display number
                    i++;
                    tableContents.append(getHTMLDeviceStatus(names.next(), true));
                }
            }
        }

        if (tableContents.isEmpty()) {
            return getEmptyTableRow();
        }
        return tableContents.toString();
    }

    /**********************************************************************/

    public String startValidator() {
        if (isRunning()) {
            String pid = getValidatorPid();
            executeCommandOnContainer("kill -9 " + pid, null);
        }
        executeCommandOnContainer("/root/bin/validator site_model/ //mqtt/mosquitto", null);
        try {
            sleep(500);
        } catch (InterruptedException e) {
            LOGGER.severe("Error waiting validator start delay");
        }
        return getStatus();
    }

    /**********************************************************************/

    public String getStatus() {
        if (isRunning()) {
            return RUNNING;
        }
        return NOT_STARTED;
    }

    /**********************************************************************/

    public boolean isRunning() {
        return executeCommandOnContainer("ps aux", VALIDATOR_JAR);
    }

    /**********************************************************************/

    private String getValidatorPid() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ps aux");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(VALIDATOR_JAR)) {
                        String[] parts = line.trim().split("\\s+");
                        return parts[0];
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error finding PID: " + e.getMessage());
        }
        return null;
    }

    /**********************************************************************/

    private List<String> getSSHCommandList() {
        List<String> commandList = new ArrayList<>();
        commandList.add("ssh");
        commandList.add("-i");
        commandList.add("/root/.ssh/id_ed25519");
        commandList.add("root@validator");
        commandList.add("-p");
        commandList.add("22");
        commandList.add("-o");
        commandList.add("StrictHostKeyChecking=no");
        commandList.add("-o");
        commandList.add("UserKnownHostsFile=/dev/null");
        return commandList;
    }

    /**********************************************************************/

    public void runRegistrar() {
        executeCommandOnContainer(
                "/root/bin/registrar site_model/ //mqtt/mosquitto",
                "Registration summary available in /root/site_model/out/registration_summary.json");
    }

    /**********************************************************************/

    private boolean executeCommandOnContainer(String command, String lineToFind) {
        List<String> commandList = getSSHCommandList();
        commandList.add(command);

        String errMsg = null;
        boolean found = false;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(commandList);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            if (lineToFind != null) {
                int exitCode = process.waitFor();

                String line;
                while ((line = reader.readLine()) != null && !found) {
                    if (line.contains(lineToFind)) {
                        found = true;
                    }
                }

                if (exitCode != 0) {
                    errMsg = "failed with exit code: " + exitCode;
                }
            }
        } catch (Exception e) {
            errMsg = e.getMessage();
        }

        if (errMsg != null) {
            LOGGER.severe("Failed to execute command on validator container: " + errMsg);
        }

        return found;
    }

    /********************************************************************************/

    JsonObject validationReport;
    JsonObject devices;
    JsonArray correctDevices;
    JsonArray missingDevices;
    JsonArray devicesInError;
    JsonArray extraDevices;

    /********************************************************************************/

    public static final String RUNNING = "Running";
    public static final String NOT_STARTED = "Not Started";
    public static final String VALIDATOR_JAR = "validator-1.0-SNAPSHOT-all.jar";

    /********************************************************************************/

    private final static Logger LOGGER = Logger.getLogger(ValidatorManager.class.getName());
}
