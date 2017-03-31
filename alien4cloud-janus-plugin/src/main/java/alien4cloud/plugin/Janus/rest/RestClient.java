/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Iterator;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.plugin.Janus.ProviderConfig;
import alien4cloud.plugin.Janus.rest.Response.AttributeResponse;
import alien4cloud.plugin.Janus.rest.Response.DeployInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.ErrorsResponse;
import alien4cloud.plugin.Janus.rest.Response.EventResponse;
import alien4cloud.plugin.Janus.rest.Response.InstanceInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.JanusError;
import alien4cloud.plugin.Janus.rest.Response.LogResponse;
import alien4cloud.plugin.Janus.rest.Response.NodeInfosResponse;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.rest.utils.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestClient {

    private static final String CHARSET = "UTF-8";

    // Default long pooling duration on Janus endpoints is 15 min
    private static final long SOCKET_TIMEOUT = 900000;
    private static final long CONNECTION_TIMEOUT = 10000;
    private static RestClient instance;
    private static ObjectMapper objectMapper;
    private ProviderConfig providerConfiguration;

    public static synchronized RestClient getInstance() {
        if (instance == null) {
            instance = new RestClient();
            RestClient.initObjectMapper();
            Unirest.setTimeouts(CONNECTION_TIMEOUT, SOCKET_TIMEOUT);
        }
        return instance;
    }

    private static void initObjectMapper() {
        RestClient.objectMapper = new ObjectMapper() {
            private com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper
                    = new com.fasterxml.jackson.databind.ObjectMapper();

            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object value) {
                try {
                    return jacksonObjectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Unirest.setObjectMapper(RestClient.objectMapper);
    }

    private static boolean isStatusCodeOk(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public void setProviderConfiguration(ProviderConfig providerConfiguration) throws PluginConfigurationException {
        this.providerConfiguration = providerConfiguration;
        if (Boolean.TRUE.equals(providerConfiguration.getInsecureTLS())) {
            SSLContext sslContext;
            try {
                sslContext = SSLContexts.custom()
                        .loadTrustMaterial(null, (chain, authType) -> true)
                        .build();
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                e.printStackTrace();
                throw new PluginConfigurationException("Failed to create SSL socket factory", e);
            }
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE);
            RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(((Long) CONNECTION_TIMEOUT).intValue())
                    .setSocketTimeout(((Long) SOCKET_TIMEOUT).intValue()).setConnectionRequestTimeout(((Long) SOCKET_TIMEOUT).intValue())
                    .build();
            CloseableHttpClient httpClient = HttpClients
                    .custom()
                    .setDefaultRequestConfig(clientConfig)
                    //                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSSLSocketFactory(sslsf)
                    .build();
            Unirest.setHttpClient(httpClient);
        }

    }

    public String postTopologyToJanus() throws Exception {
        final InputStream stream;

        stream = new FileInputStream(new File("topology.zip"));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();

        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlJanus() + "/deployments")
                .header("accept", "application/json")
                .header("Content-Type", "application/zip")
                .body(bytes)
                .asJson();


        if (!postResponse.getStatusText().equals("Created")) {
            throw new Exception("Janus returned an error ?");
        }

        return postResponse.getHeaders().getFirst("Location");
    }

    /**
     * Scale a node
     *
     * @param deploymentUrl returned by Janus at deployment: deployment/<deployment_id>
     * @param nodeName
     * @param delta
     *
     * @return
     *
     * @throws Exception
     */
    public String scaleNodeInJanus(String deploymentUrl, String nodeName, int delta) throws Exception {
        HttpResponse<JsonNode> postResponse =
                Unirest.post(providerConfiguration.getUrlJanus() + deploymentUrl + "/scale/" + nodeName + "?delta=" + delta)
                        .header("accept", "application/json")
                        .asJson();
        if (postResponse.getStatus() != 202) {
            log.info("Janus returned an error : " + postResponse.getStatusText());
            throw new Exception("Janus returned an error : " + postResponse.getStatus());
        }

        String ret = postResponse.getHeaders().getFirst("Location");
        log.info("Scaling accepted: " + ret);
        return ret;
    }

    public String getStatusFromJanus(String deploymentUrl) throws Exception {
        HttpResponse<JsonNode> res = Unirest.get(providerConfiguration.getUrlJanus() + deploymentUrl)
                .header("accept", "application/json")
                .asJson();


        JSONObject obj = res.getBody().getObject();

        if (!obj.has("status")) {
            throw new Exception("getStatusFromJanus : Janus returned an error");
        }

        return obj.getString("status");
    }

    public DeployInfosResponse getDeploymentInfosFromJanus(String deploymentUrl) throws Exception {
        HttpResponse<DeployInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlJanus() + deploymentUrl)
                .header("accept", "application/json")
                .asObject(DeployInfosResponse.class);
        return deployRes.getBody();
    }

    public NodeInfosResponse getNodesInfosFromJanus(String nodeInfoUrl) throws Exception {
        HttpResponse<NodeInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlJanus() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(NodeInfosResponse.class);
        return deployRes.getBody();
    }

    public InstanceInfosResponse getInstanceInfosFromJanus(String nodeInfoUrl) throws Exception {
        HttpResponse<InstanceInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlJanus() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(InstanceInfosResponse.class);
        return deployRes.getBody();
    }

    public AttributeResponse getAttributeFromJanus(String nodeInfoUrl) throws Exception {
        HttpResponse<AttributeResponse> deployRes = Unirest.get(providerConfiguration.getUrlJanus() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(AttributeResponse.class);
        return deployRes.getBody();
    }

    public LogResponse getLogFromJanus(String deploymentUrl, int index) throws Exception {
        HttpResponse<JsonNode> logRes =
                Unirest.get(providerConfiguration.getUrlJanus() + deploymentUrl + "/logs?index=" + index + "&filter=")
                        .header("accept", "application/json")
                        .asJson();
        this.checkRestErrors(logRes);
        return objectMapper.readValue(new String(IOUtils.toByteArray(logRes.getRawBody()), CHARSET), LogResponse.class);
    }

    public EventResponse getEventFromJanus(String deploymentUrl, int index) throws Exception {
        HttpResponse<JsonNode> eventResponse =
                Unirest.get(providerConfiguration.getUrlJanus() + deploymentUrl + "/events?index=" + index + "&filter=")
                        .header("accept", "application/json")
                        .asJson();
        this.checkRestErrors(eventResponse);
        return objectMapper.readValue(new String(IOUtils.toByteArray(eventResponse.getRawBody()), CHARSET), EventResponse.class);
    }

    public String undeployJanus(String deploymentUrl) throws UnirestException {
        return Unirest.delete(providerConfiguration.getUrlJanus() + deploymentUrl)
                .header("accept", "application/json")
                .asJson()
                .getStatusText();

    }

    public String postCustomCommandToJanus(String deploymentUrl, NodeOperationExecRequest request) throws Exception {
        final InputStream stream;


        JSONObject jobject = new JSONObject();
        jobject.put("node", request.getNodeTemplateName());
        jobject.put("name", request.getOperationName());
        jobject.put("inputs", request.getParameters());

        final byte[] bytes = jobject.toString().getBytes();

        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlJanus() + deploymentUrl + "/custom")
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .body(bytes)
                .asJson();

        System.out.println(">>> Response status for custom POST is : " + postResponse.getStatusText());
        if (!postResponse.getStatusText().equals("Accepted")) {
            throw new Exception("Janus returned an error :" + postResponse.getStatus());
        }

        String ret = postResponse.getHeaders().getFirst("Location");
        //log.info("Custom operation accepted : " + ret);
        return ret;
    }

    public String postWorkflowToJanus(String deploymentUr, String workflowName) throws Exception {
        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlJanus() + deploymentUr + "/workflows/" + workflowName)
                .header("accept", "application/json")
                .asJson();
        if (!postResponse.getStatusText().equals("Created")) {
            throw new Exception("Janus returned an error :" + postResponse.getStatus());
        }

        log.info("Workflow accepted : " +  postResponse.getHeaders().getFirst("Location"));
        return postResponse.getHeaders().getFirst("Location");
    }

    private void checkRestErrors(HttpResponse<?> httpResponse) throws Exception {
        if (!isStatusCodeOk(httpResponse.getStatus())) {
            ErrorsResponse errors =
                    this.objectMapper.readValue(new String(IOUtils.toByteArray(httpResponse.getRawBody()), CHARSET), ErrorsResponse.class);
            JanusError error = errors.getErrors().iterator().next();
            throw JanusRestException.fromJanusError(error);
        }
    }
}
