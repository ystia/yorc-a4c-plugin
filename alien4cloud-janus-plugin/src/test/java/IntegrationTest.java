import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertTrue;


public class IntegrationTest {

    @Test
    public void integrationTest() {
        String result = "";
        try {
            String cmd = "python ./src/integration-test/scripts/integration-test.py";

            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));


            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
                result += s + "\n";
            }


            boolean hasStdError = false;
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
                hasStdError = true;
            }
            assertTrue("StdError from output", !hasStdError);


            assertTrue("Output contains error", !result.contains("error"));
            assertTrue("Output contains errno", !result.contains("errno"));

        } catch (IOException e) {
            e.printStackTrace();
            assert (false);
        }
    }
}
