import java.util.regex.Pattern;
public class TestPipe {
    public static void main(String[] args) {
        String topic1 = "/r/ZZ-TRI-FECTA/d/OLI-1/config";
        String topic2 = "/r/ZZ-TRI-FECTA/d/OLI-1/c/control/config/update";
        System.out.println(topic1.split("/", 12).length);
        for(String p : topic2.split("/", 12)) System.out.println("  " + p);
    }
}
