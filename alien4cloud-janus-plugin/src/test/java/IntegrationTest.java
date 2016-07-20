import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class IntegrationTest {

    @Test
    public void integrationTest() {
        String result = "";
        try {
            String cmd = "python ./src/integration-test/scripts/A4CDriver/Main.py ./src/integration-test/scripts/A4CDriver/A4CDriverConf_Template.json";

            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));


            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
                result += s + "\n";
            }



            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
                assert(false);
            }


            assert(!result.contains("error"));
            assert(!result.contains("errno"));

        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }
    }
}
