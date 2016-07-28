/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by a628490 on 13/05/2016.
 */
@Slf4j
public class ExecPython {

    public List<String> nodeInstall() {
        List<String> result = new ArrayList<>();
        try {
            String cmd;
            cmd = "python computeInstallTest.py";
            log.info("command python :" + cmd);
            Process p = Runtime.getRuntime().exec(cmd);

            //Buffer
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
                result.add(s);
            }

            // read any errors from the attempted command
//            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            System.out.println("----- End script -----\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<String> nodeInstall(String gpuType) {
        List<String> result = new ArrayList<>();
        try {
            String cmd;
            cmd = "python SlurmSshApi.py";
            log.info("command python :" + cmd);
            Process p = Runtime.getRuntime().exec(cmd);

            //Buffer
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
                result.add(s);
            }

            // read any errors from the attempted command
//            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            System.out.println("----- End script -----\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<String> componentInstall(List<String> nodeId, String url, String scriptPath, String gpu) {
        List<String> result = new ArrayList<>();
        try {
            String node;
            if (gpu.equals("none")) {
                node = "mo116";
            } else {
                node = "mo80";
            }

            String cmd = "python " + scriptPath + " " + node + " " + url + " " + gpu;
            log.info("command python :" + cmd);
            Process p = Runtime.getRuntime().exec(cmd);

            //Buffer
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
                result.add(s);
            }

            // read any errors from the attempted command
//            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            System.out.println("----- End script -----\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


}
