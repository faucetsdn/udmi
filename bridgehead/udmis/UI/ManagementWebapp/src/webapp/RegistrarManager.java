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

public class RegistrarManager {

    public RegistrarManager() {
        getRegistrarSummary();
    }

    /********************************************************************************/

    private String getRegistrarSummary(){
        try (Reader reader = new FileReader("/root/site_model/out/registration_summary.json")) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            registrarSummary = jsonElement.getAsJsonObject();
            devicesStatus = getJsonObject("status", registrarSummary);
        } catch (Exception e) {
            String errMsg = "Error getting registrar summary: " + e.getMessage();
            LOGGER.severe(errMsg);
            return errMsg;
        }
        return null;
    }

    /********************************************************************************/

    public String getDeviceStatus() {
        return getDeviceStatus(null);
    }

    /********************************************************************************/

    public String getDeviceStatus(String deviceName) {
        StringBuilder tableContents = new StringBuilder();

        if (devicesStatus != null) {
            if (isValidString(deviceName)) {
                tableContents.append(getHTMLDeviceStatus(deviceName));
            } else {
                Iterator<String> names = devicesStatus.keySet().iterator();
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

    /********************************************************************************/

    private String getHTMLDeviceStatus(String deviceName){
        return getHTMLDeviceStatus(deviceName, false);
    }

    private String getHTMLDeviceStatus(String deviceName, boolean exactMatch) {

        List<String> matchingKeys =
                exactMatch
                        ? devicesStatus.keySet().stream()
                        .filter(key -> key.equals(deviceName))
                        .limit(100)
                        .toList()

                        : devicesStatus.keySet().stream()
                        .filter(key -> key.toLowerCase().contains(deviceName.toLowerCase()))
                        .limit(100)
                        .toList();


        StringBuilder stringBuilder = new StringBuilder();

        String statusFormat = """
                <tr>
                    <td>%s</td>
                    <td>%s</td>
                </tr>
                """;

        for (String name : matchingKeys) {
            String status = getString(name, devicesStatus);

            stringBuilder.append(String.format(statusFormat,
                    name,
                    status));
        }

        return stringBuilder.toString();
    }

    /********************************************************************************/

    public String getLastRegistrarRun() {
        String errMsg = getRegistrarSummary();

        if (registrarSummary != null && !registrarSummary.isEmpty()){
            return getFormattedTimestamp(getString("timestamp", registrarSummary));
        }

        if(errMsg != null && errMsg.contains("(No such file or directory)")){
            return " --- ";
        }
        return " --- Error ---";
    }

    /********************************************************************************/

    JsonObject registrarSummary;
    JsonObject devicesStatus;

    private final static Logger LOGGER = Logger.getLogger(RegistrarManager.class.getName());
}
