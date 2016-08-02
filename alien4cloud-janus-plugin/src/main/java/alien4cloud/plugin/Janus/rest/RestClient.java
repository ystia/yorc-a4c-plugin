/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.rest;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.io.*;

public class RestClient {

    final String janusUrl = "http://localhost:8800";

    public String postTopologyToJanus() {
        final InputStream stream;
        try {
            stream = new FileInputStream(new File("welcome.zip"));
            final byte[] bytes = new byte[stream.available()];
            stream.read(bytes);
            stream.close();

            HttpResponse<JsonNode> postResponse = Unirest.post(janusUrl + "/deployments")
                    .header("accept", "application/json")
                    .header("Content-Type", "application/zip")
                    .body(bytes)
                    .asJson();

            return postResponse.getStatusText();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnirestException e) {
            System.out.println(e.getMessage());
        }

        return "error";

    }


    public String getStatusFromJanus(String id) {
        try {
            return Unirest.get(janusUrl + "/deployments/" + id).asJson().getStatusText();
        } catch (UnirestException e) {
            e.printStackTrace();
            return "error";
        }
    }

    public String undeployJanus(String id) {
        try {
            return Unirest.delete(janusUrl + "/deployments/" + id).asJson().getStatusText();
        } catch (UnirestException e) {
            e.printStackTrace();
            return "error";
        }
    }


}
