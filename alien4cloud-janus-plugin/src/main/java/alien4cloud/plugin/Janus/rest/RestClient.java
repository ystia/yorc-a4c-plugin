/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.rest;

import alien4cloud.plugin.Janus.ProviderConfig;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class RestClient {

    private ProviderConfig providerConfiguration;

    public String postTopologyToJanus() throws Exception {
        final InputStream stream;

        stream = new FileInputStream(new File("welcome.zip"));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();

        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlJanus() + "/deployments")
                .header("accept", "application/json")
                .header("Content-Type", "application/zip")
                .body(bytes)
                .asJson();


        if(!postResponse.getStatusText().equals("Created")) {
            throw new Exception("Janus returned an error ?");
        }

        return postResponse.getHeaders().getFirst("Location");
    }


    public String getStatusFromJanus(String deploymentUrl) throws Exception {
        HttpResponse<JsonNode> res = Unirest.get(providerConfiguration.getUrlJanus() + deploymentUrl)
                .header("accept", "application/json")
                .asJson();


        JSONObject obj = res.getBody().getObject();

        if(!obj.has("status")) {
            throw new Exception("getStatusFromJanus : Janus returned an error");
        }

        return obj.getString("status");
    }

    public String undeployJanus(String deploymentUrl) throws UnirestException {
        return Unirest.delete(providerConfiguration.getUrlJanus() + deploymentUrl)
                .header("accept", "application/json")
                .asJson()
                .getStatusText();

    }


}
