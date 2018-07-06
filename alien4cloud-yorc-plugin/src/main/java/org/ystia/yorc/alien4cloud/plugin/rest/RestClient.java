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
import java.io.InputStream;
import java.util.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.NodeOperationExecRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.ystia.yorc.alien4cloud.plugin.ProviderConfig;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.*;

@Slf4j
public class RestClient {
    // Default long pooling duration on Yorc endpoints is 15 min
    private static final long SOCKET_TIMEOUT = 900000;
    private static final long CONNECTION_TIMEOUT = 10000;
    private ProviderConfig providerConfiguration;

    private RestTemplate restTemplate;
    private static ObjectMapper objectMapper = new ObjectMapper();

    public RestClient() {
    }

    private static boolean isStatusCodeOk(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }


    public void setProviderConfiguration(ProviderConfig providerConfiguration) throws PluginConfigurationException {
        this.providerConfiguration = providerConfiguration;
        log.debug("setProviderConfiguration YorcURL=" + providerConfiguration.getUrlYorc());
        RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(((Long) CONNECTION_TIMEOUT).intValue())
                .setSocketTimeout(((Long) SOCKET_TIMEOUT).intValue()).setConnectionRequestTimeout(((Long) SOCKET_TIMEOUT).intValue())
                .build();

        PoolingHttpClientConnectionManager poolHttpConnManager = new PoolingHttpClientConnectionManager();
        poolHttpConnManager.setDefaultMaxPerRoute(20);
        poolHttpConnManager.setMaxTotal(20);
        SocketConfig sockConf = SocketConfig.custom().setSoTimeout(((Long) SOCKET_TIMEOUT).intValue()).build();
        poolHttpConnManager.setDefaultSocketConfig(sockConf);
        CloseableHttpClient httpClient;
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
            httpClient = HttpClientBuilder.create().useSystemProperties()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .setSSLSocketFactory(sslsf)
                    .build();
        } else if (providerConfiguration.getUrlYorc().startsWith("https")){
            if(System.getProperty("javax.net.ssl.keyStore") == null || System.getProperty("javax.net.ssl.keyStorePassword") == null){
                log.warn("Using SSL but you didn't provide client keystore and password. This means that if required by Yorc client authentication will fail.\n" +
                        "Please use -Djavax.net.ssl.keyStore <keyStorePath> -Djavax.net.ssl.keyStorePassword <password> while starting java VM");
            }
            if(System.getProperty("javax.net.ssl.trustStore") == null || System.getProperty("javax.net.ssl.trustStorePassword") == null){
                log.warn("You didn't provide client trustore and password. Using defalut one \n" +
                        "Please use -Djavax.net.ssl.trustStore <trustStorePath> -Djavax.net.ssl.trustStorePassword <password> while starting java VM");
            }

            SSLContext sslContext = SSLContexts.createSystemDefault();
            httpClient = HttpClientBuilder.create().useSystemProperties()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .setSslcontext(sslContext)
                    .build();
        } else {
            httpClient = HttpClientBuilder.create().useSystemProperties()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .build();
        }

        // Instantiate restTemplate
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setHttpClient(httpClient);
        restTemplate = new RestTemplate(requestFactory);

        // Display deployments
        logDeployments();
    }

    /**
     * Common used method to send REST request
     * @param targetUrl
     * @param httpMethod
     * @param responseType
     * @param httpEntity
     * @param <T>
     * @return ResponseEntity<T>
     * @throws Exception
     */
    private <T> ResponseEntity<T> sendRequest(String targetUrl, HttpMethod httpMethod, Class<T> responseType, HttpEntity httpEntity) throws Exception {
        ResponseEntity<T> resp = null;
        try {
            log.debug("Request URI         : {}", targetUrl);
            log.debug("Method      : {}", httpMethod);
            if (httpEntity.getHeaders() != null) {
                log.debug("Headers     : {}", httpEntity.getHeaders());
            }
            if (httpEntity.getBody() != null) {
                log.debug("Request body: {}", httpEntity.getBody().toString());
            }

            resp = restTemplate.exchange(targetUrl, httpMethod, httpEntity, responseType);
            log.debug("Response Status code  : {}", resp.getStatusCode());
            if (resp.getHeaders() != null) {
                log.debug("Response Headers      : {}", resp.getHeaders());
            }
            if (resp.getBody() != null) {
                log.debug("Response Body: {}", resp.getBody().toString());
            }
            return resp;
        }
        catch (HttpStatusCodeException e) {
            log.debug("Status code exception: {}", e.getRawStatusCode());
            if (!isStatusCodeOk(e.getRawStatusCode())) {
                log.debug("Response Body errors: {}", e.getResponseBodyAsString());
                ErrorsResponse errors = objectMapper.readValue(e.getResponseBodyAsString(), ErrorsResponse.class);
                YorcError error = errors.getErrors().iterator().next();
                throw YorcRestException.fromYorcError(error);
            }
        }
        catch (RestClientException e) {
            throw new Exception("An error occurred while calling " + httpMethod + " " + targetUrl, e);
        }
        return resp;
    }


    /**
     * This allows to build an HTTPEntity object with body and default headers with JSON ACCEPT
     * @param body
     * @return HttpEntity
     */
    public HttpEntity buildHttpEntityWithDefaultHeader(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (body != null) {
            return new HttpEntity<>(body, headers);
        } else {
            return new HttpEntity<>(headers);
        }
    }

    /**
     * This allows to log deployments
     */
    public void logDeployments() {
        try {
            DeployInfoResponseArray deployments = sendRequest(providerConfiguration.getUrlYorc() + "/deployments", HttpMethod.GET, DeployInfoResponseArray.class, buildHttpEntityWithDefaultHeader(null)).getBody();
            if (deployments != null && deployments.getDeployments() != null && deployments.getDeployments().length > 0) {
                Arrays.asList(deployments).forEach(item -> log.debug("Found a deployment: " + item));
            } else {
                log.debug("No deployment found");
            }

        }
        catch (Exception e) {
            log.warn("Unable to retrieve deployments due to: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send a topology to Yorc
     * @param deploymentId
     * @return String
     * @throws Exception
     */
    public String sendTopologyToYorc(String deploymentId) throws Exception {
        try (InputStream stream = new FileInputStream(new File("topology.zip")))
        {
            // Get file to upload
            final byte[] bytes = new byte[stream.available()];
            stream.read(bytes);
            stream.close();

            // Get specific headers and body
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
            HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

            String targetUrl = providerConfiguration.getUrlYorc() + "/deployments/" + deploymentId;
            ResponseEntity<String> resp = sendRequest(targetUrl, HttpMethod.PUT, String.class, request);
            if (!resp.getStatusCode().getReasonPhrase().equals("Created")){
                throw new Exception("sendTopologyToYorc: Yorc returned an unexpected status: " + resp.getStatusCode().getReasonPhrase());
            }
            return resp.getHeaders().getFirst("Location");
        }
    }

    /**
     * Scale a node
     *
     * @param deploymentUrl returned by Yorc at deployment: deployment/<deployment_id>
     * @param nodeName
     * @param delta
     *
     * @return String
     *
     * @throws Exception
     */
    public String postScalingToYorc(String deploymentUrl, String nodeName, int delta) throws Exception {
        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + deploymentUrl + "/scale/" + nodeName + "?delta=" + delta, HttpMethod.POST, String.class, buildHttpEntityWithDefaultHeader(null));
        if (resp.getStatusCodeValue() != 202) {
            log.warn("Yorc returned an error : " + resp.getStatusCodeValue());
            throw new Exception("postScalingToYorc: Yorc returned an error : " + resp.getStatusCodeValue());
        }

        return resp.getHeaders().getFirst("Location");
    }

    /**
     * Return the Deployment Status from Yorc
     * @param deploymentUrl
     * @return Status Look at yorc/deployments/structs.go to see all possible values
     * @throws Exception
     */
    public String getStatusFromYorc(String deploymentUrl) throws Exception {
        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + deploymentUrl, HttpMethod.GET, String.class, buildHttpEntityWithDefaultHeader(null));
        String status = null;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(resp.getBody());
        JsonNode node = root.path("status");
        if (node != null) {
            status =  node.asText();
        }
        if (status == null) {
            throw new Exception("getStatusFromYorc returned no status");
        }
        log.debug("Status is:" + status + " for deployment: " + deploymentUrl);
        return status;
    }

    /**
     * This allows to Get deployment info
     * @param deploymentUrl
     * @return DeployInfosResponse
     * @throws Exception
     */
    public DeployInfosResponse getDeploymentInfosFromYorc(String deploymentUrl) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + deploymentUrl, HttpMethod.GET, DeployInfosResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * This allows to get nodes info
     * @param nodeInfoUrl
     * @return NodeInfosResponse
     * @throws Exception
     */
    public NodeInfosResponse getNodesInfosFromYorc(String nodeInfoUrl) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + nodeInfoUrl, HttpMethod.GET, NodeInfosResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * this allows to Get instance info
     * @param nodeInfoUrl
     * @return InstanceInfosResponse
     * @throws Exception
     */
    public InstanceInfosResponse getInstanceInfosFromYorc(String nodeInfoUrl) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + nodeInfoUrl, HttpMethod.GET, InstanceInfosResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * this allows to Get attributes
     * @param nodeInfoUrl
     * @return AttributeResponse
     * @throws Exception
     */
    public AttributeResponse getAttributeFromYorc(String nodeInfoUrl) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + nodeInfoUrl, HttpMethod.GET, AttributeResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * this allows to Get logs
     * @param index
     * @return LogResponse
     * @throws Exception
     */
    public LogResponse getLogFromYorc(int index) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + "/logs?index=" + index, HttpMethod.GET, LogResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * This allows to Get events
     * @param index
     * @return EventResponse
     * @throws Exception
     */
    public EventResponse getEventFromYorc(int index) throws Exception {
        return sendRequest(providerConfiguration.getUrlYorc() + "/events?index=" + index, HttpMethod.GET, EventResponse.class, buildHttpEntityWithDefaultHeader(null)).getBody();
    }

    /**
     * This allows to undeploy an application
     * @param deploymentUrl
     * @param purge
     * @return String
     * @throws Exception
     */
    public String undeploy(String deploymentUrl, boolean purge) throws Exception {
        log.debug("undeploy " + deploymentUrl + "with purge = " + purge);
        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + deploymentUrl + (purge ? "?purge" : ""), HttpMethod.DELETE, String.class, buildHttpEntityWithDefaultHeader(null));
        return resp.getHeaders().getFirst("Location");
    }

    /**
     * This allows to Post stopping task
     * @param taskUrl
     * @return String
     * @throws Exception
     */
    public String stopTask(String taskUrl) throws Exception {
        log.debug("stop task: {}", taskUrl);
        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + taskUrl, HttpMethod.DELETE, String.class, buildHttpEntityWithDefaultHeader(null));
        return resp.getHeaders().getFirst("Location");
    }

    /**
     * This allows to send Post custom command
     * @param deploymentUrl
     * @param request
     * @return String
     * @throws Exception
     */
    public String postCustomCommandToYorc(String deploymentUrl, NodeOperationExecRequest request) throws Exception {
        // Get specific headers and body
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("node", request.getNodeTemplateName());
        jsonObj.put("name", request.getOperationName());
        jsonObj.put("inputs", request.getParameters());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<byte[]> httpEntity = new HttpEntity<>(jsonObj.toString().getBytes(), headers);

        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + deploymentUrl + "/custom", HttpMethod.POST, String.class, httpEntity);
        if (resp.getStatusCode().getReasonPhrase() == null) {
            throw new Exception("postCustomCommandToYorc returned no status");
        } else if (!resp.getStatusCode().getReasonPhrase().equals("Accepted")){
            throw new Exception("postCustomCommandToYorc: Yorc returned an unexpected status: " + resp.getStatusCode().getReasonPhrase());
        }
        return resp.getHeaders().getFirst("Location");
    }

    /**
     * POST a workflow to Yorc
     * @param deploymentUr
     * @param workflowName
     * @param inputs
     * @return String
     * @throws Exception
     */
    public String postWorkflowToYorc(String deploymentUr, String workflowName, Map<String, Object> inputs) throws Exception {
        ResponseEntity<String> resp = sendRequest(providerConfiguration.getUrlYorc() + deploymentUr + "/workflows/" + workflowName, HttpMethod.POST, String.class, buildHttpEntityWithDefaultHeader(null));
        if (resp.getStatusCode().getReasonPhrase() == null) {
            throw new Exception("postCustomCommandToYorc returned no status");
        } else if (!resp.getStatusCode().getReasonPhrase().equals("Created")){
            throw new Exception("postWorkflowToYorc: Yorc returned an unexpected status: " + resp.getStatusCode().getReasonPhrase());
        }
        return resp.getHeaders().getFirst("Location");
    }
 }
