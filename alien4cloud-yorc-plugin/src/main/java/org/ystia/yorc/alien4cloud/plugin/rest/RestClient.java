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
package org.ystia.yorc.alien4cloud.plugin.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.NodeOperationExecRequest;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.ystia.yorc.alien4cloud.plugin.ProviderConfig;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.*;

@Slf4j
public class RestClient {

    private static final String CHARSET = "UTF-8";

    // Default long pooling duration on Yorc endpoints is 15 min
    private static final long SOCKET_TIMEOUT = 900000;
    private static final long CONNECTION_TIMEOUT = 10000;
    private static ObjectMapper objectMapper;
    private ProviderConfig providerConfiguration;

    public RestClient() {
        RestClient.initObjectMapper();
        Unirest.setTimeouts(CONNECTION_TIMEOUT, SOCKET_TIMEOUT);
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
        log.debug("setProviderConfiguration YorcURL=" + providerConfiguration.getUrlYorc());
        try {
            getDeployments();
        } catch (UnirestException e) {
            log.warn("Cannot access Yorc: " + e.getCause());
            throw new PluginConfigurationException("Cannot access Yorc: " + e.getCause());
        }
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

    /**
     * Get the list of deployments known by Yorc
     * @return List of deployments
     */
    public List<String> getDeployments() throws UnirestException {
        List<String> ret = new ArrayList<>();
        String fullUrl = providerConfiguration.getUrlYorc() + "/deployments";
        log.debug("getDeployments " + fullUrl);
        HttpResponse<JsonNode> res = Unirest.get(fullUrl)
                .header("accept", "application/json")
                .asJson();
        if (res == null) {
            log.debug("Cannot reach Yorc: null response");
            return null;
        }
        if (res.getBody() != null) {
            JSONObject obj = res.getBody().getObject();
            JSONArray array = obj.getJSONArray("deployments");
            for (int i = 0 ; i < array.length() ; i++) {
                String depl = array.getJSONObject(i).getString("href");
                log.debug("Found a deployment in Yorc: " + depl);
                ret.add(depl);
            }
        }
        return ret;
    }

    /**
     * Send a topology to Yorc
     * @param deploymentId
     * @return
     * @throws Exception
     */
    public String sendTopologyToYorc(String deploymentId) throws Exception {
        final InputStream stream;

        stream = new FileInputStream(new File("topology.zip"));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();

        HttpResponse<JsonNode> putResponse = Unirest.put(providerConfiguration.getUrlYorc() + "/deployments/" + deploymentId)
                .header("accept", "application/json")
                .header("Content-Type", "application/zip")
                .body(bytes)
                .asJson();


        if (!putResponse.getStatusText().equals("Created")) {
            throw new Exception("sendTopologyToYorc: Yorc returned an error : " + putResponse.getStatus());
        }

        return putResponse.getHeaders().getFirst("Location");
    }

    /**
     * Scale a node
     *
     * @param deploymentUrl returned by Yorc at deployment: deployment/<deployment_id>
     * @param nodeName
     * @param delta
     *
     * @return
     *
     * @throws Exception
     */
    public String postScalingToYorc(String deploymentUrl, String nodeName, int delta) throws Exception {
        HttpResponse<JsonNode> postResponse =
                Unirest.post(providerConfiguration.getUrlYorc() + deploymentUrl + "/scale/" + nodeName + "?delta=" + delta)
                        .header("accept", "application/json")
                        .asJson();
        if (postResponse.getStatus() != 202) {
            log.warn("Yorc returned an error : " + postResponse.getStatusText());
            throw new Exception("postScalingToYorc: Yorc returned an error : " + postResponse.getStatus());
        }

        return postResponse.getHeaders().getFirst("Location");
    }

    /**
     * Return the Deployment Status from Yorc
     * @param deploymentUrl
     * @return Status Look at yorc/deployments/structs.go to see all possible values
     * @throws Exception
     */
    public String getStatusFromYorc(String deploymentUrl) throws Exception {
        String fullUrl = providerConfiguration.getUrlYorc() + deploymentUrl;
        log.debug("getStatusFromYorc " + fullUrl);
        HttpResponse<JsonNode> res = Unirest.get(fullUrl)
                .header("accept", "application/json")
                .asJson();

        checkRestErrors(res);

        JSONObject obj = res.getBody().getObject();
        if (!obj.has("status")) {
            throw new Exception("getStatusFromYorc returned no status");
        }
        return obj.getString("status");
    }

    public DeployInfosResponse getDeploymentInfosFromYorc(String deploymentUrl) throws Exception {
        HttpResponse<DeployInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlYorc() + deploymentUrl)
                .header("accept", "application/json")
                .asObject(DeployInfosResponse.class);
        return deployRes.getBody();
    }

    public NodeInfosResponse getNodesInfosFromYorc(String nodeInfoUrl) throws Exception {
        HttpResponse<NodeInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlYorc() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(NodeInfosResponse.class);
        return deployRes.getBody();
    }

    public InstanceInfosResponse getInstanceInfosFromYorc(String nodeInfoUrl) throws Exception {
        HttpResponse<InstanceInfosResponse> deployRes = Unirest.get(providerConfiguration.getUrlYorc() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(InstanceInfosResponse.class);
        return deployRes.getBody();
    }

    public AttributeResponse getAttributeFromYorc(String nodeInfoUrl) throws Exception {
        HttpResponse<AttributeResponse> deployRes = Unirest.get(providerConfiguration.getUrlYorc() + nodeInfoUrl)
                .header("accept", "application/json")
                .asObject(AttributeResponse.class);
        return deployRes.getBody();
    }

    public LogResponse getLogFromYorc(int index) throws Exception {
        HttpResponse<JsonNode> logRes =
                Unirest.get(providerConfiguration.getUrlYorc() + "/logs?index=" + index + "&filter=")
                        .header("accept", "application/json")
                        .asJson();
        this.checkRestErrors(logRes);
        return objectMapper.readValue(new String(IOUtils.toByteArray(logRes.getRawBody()), CHARSET), LogResponse.class);
    }

    public EventResponse getEventFromYorc(int index) throws Exception {
        HttpResponse<JsonNode> eventResponse =
                Unirest.get(providerConfiguration.getUrlYorc() + "/events?index=" + index + "&filter=")
                        .header("accept", "application/json")
                        .asJson();
        this.checkRestErrors(eventResponse);
        return objectMapper.readValue(new String(IOUtils.toByteArray(eventResponse.getRawBody()), CHARSET), EventResponse.class);
    }

    public String undeploy(String deploymentUrl, boolean purge) throws Exception {
        log.debug("undeploy " + deploymentUrl + "with purge = " + purge);
        HttpResponse<JsonNode> res = Unirest.delete(providerConfiguration.getUrlYorc() + deploymentUrl + (purge ? "?purge" : "") )
                .header("accept", "application/json")
                .asJson();

        log.debug(">>> Response status for undeploy is : " + res.getStatusText());
        this.checkRestErrors(res);
        return res.getHeaders().getFirst("Location");
    }

    public String stopTask(String taskUrl) throws UnirestException {
        log.debug("stop task " + taskUrl);
        HttpResponse<JsonNode> res = Unirest.delete(providerConfiguration.getUrlYorc() + taskUrl)
                .header("accept", "application/json")
                .asJson();
        return res.getHeaders().getFirst("Location");
    }

    public String postCustomCommandToYorc(String deploymentUrl, NodeOperationExecRequest request) throws Exception {
        JSONObject jobject = new JSONObject();
        jobject.put("node", request.getNodeTemplateName());
        jobject.put("name", request.getOperationName());
        jobject.put("inputs", request.getParameters());

        final byte[] bytes = jobject.toString().getBytes();

        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlYorc() + deploymentUrl + "/custom")
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .body(bytes)
                .asJson();

        log.debug(">>> Response status for custom POST is : " + postResponse.getStatusText());
        if (!postResponse.getStatusText().equals("Accepted")) {
            throw new Exception("postCustomCommandToYorc: Yorc returned an error :" + postResponse.getStatus());
        }

        String ret = postResponse.getHeaders().getFirst("Location");
        return ret;
    }

    /**
     * POST a workflow to Yorc
     * @param deploymentUr
     * @param workflowName
     * @param inputs
     * @return
     * @throws Exception
     */
    public String postWorkflowToYorc(String deploymentUr, String workflowName, Map<String, Object> inputs) throws Exception {
        HttpResponse<JsonNode> postResponse = Unirest.post(providerConfiguration.getUrlYorc() + deploymentUr + "/workflows/" + workflowName)
                .header("accept", "application/json")
                .asJson();
        if (!postResponse.getStatusText().equals("Created")) {
            throw new Exception("postWorkflowToYorc: Yorc returned an error :" + postResponse.getStatus());
        }
        String ret = postResponse.getHeaders().getFirst("Location");
        log.info("Workflow accepted: " + ret);
        return ret;
    }

    private void checkRestErrors(HttpResponse<?> httpResponse) throws Exception {
        if (!isStatusCodeOk(httpResponse.getStatus())) {
            ErrorsResponse errors =
                    this.objectMapper.readValue(new String(IOUtils.toByteArray(httpResponse.getRawBody()), CHARSET), ErrorsResponse.class);
            YorcError error = errors.getErrors().iterator().next();
            throw YorcRestException.fromYorcError(error);
        }
    }
}
