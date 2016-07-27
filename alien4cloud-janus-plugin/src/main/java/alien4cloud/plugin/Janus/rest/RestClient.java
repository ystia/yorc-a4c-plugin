/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.rest;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RestClient {

    public void postTopologyToJanus() {
        System.out.println(executeCommand("./curlUploadZipToJanus.sh"));
    }

    private static String executeCommand(String command) {
        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            //p.waitFor();
            String line = "";
            while ((line = reader.readLine()) != null) {
                System.out.println("line="+line);
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();

    }
}
