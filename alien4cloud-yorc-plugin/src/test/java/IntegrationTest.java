/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
