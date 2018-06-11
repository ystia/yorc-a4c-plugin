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
import java.net.URI;
import java.util.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.NodeOperationExecRequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.ystia.yorc.alien4cloud.plugin.ProviderConfig;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.*;

@Slf4j
public class RestClient {

    private static final String CHARSET = "UTF-8";

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
            httpClient = HttpClients
                    .custom()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .setSSLSocketFactory(sslsf)
                    .build();
        }else if (providerConfiguration.getUrlYorc().startsWith("https")){
            if(System.getProperty("javax.net.ssl.keyStore") == null || System.getProperty("javax.net.ssl.keyStorePassword") == null){
                log.warn("Using SSL but you didn't provide client keystore and password. This means that if required by Yorc client authentication will fail.\n" +
                        "Please use -Djavax.net.ssl.keyStore <keyStorePath> -Djavax.net.ssl.keyStorePassword <password> while starting java VM");
            }
            if(System.getProperty("javax.net.ssl.trustStore") == null || System.getProperty("javax.net.ssl.trustStorePassword") == null){
                log.warn("You didn't provide client trustore and password. Using defalut one \n" +
                        "Please use -Djavax.net.ssl.trustStore <trustStorePath> -Djavax.net.ssl.trustStorePassword <password> while starting java VM");
            }

            SSLContext sslContext = SSLContexts.createSystemDefault();

            httpClient = HttpClients
                    .custom()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .setSslcontext(sslContext)
                    .build();
        } else {
            httpClient = HttpClients
                    .custom()
                    .setConnectionManager(poolHttpConnManager)
                    .setDefaultRequestConfig(clientConfig)
                    .build();
        }

        // Instantiate restTemplate
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);


        //requestFactory.setHttpClient(httpClient);
        restTemplate = new RestTemplate(requestFactory);


        // custom json
//        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
//        messageConverters.add(new MappingJackson2HttpMessageConverter(objectMapper));
//        restTemplate.setMessageConverters(messageConverters);

        logDeployments();
    }

    private <T> T callForEntity(String targetUrl, HttpMethod httpMethod, Class<T> responseType, HttpEntity httpEntity) throws Exception {
        T responseEntity = null;
        try {
            ResponseEntity<String> resp = restTemplate.exchange(targetUrl, httpMethod, httpEntity, String.class);
            log.debug("response = " + resp.getBody());
            log.debug("code = " + resp.getStatusCodeValue());
            return objectMapper.readValue(resp.getBody(), responseType);
        }
        catch (HttpStatusCodeException e) {
            if (!isStatusCodeOk(e.getRawStatusCode())) {
                log.debug("e.getResponseBodyAsString() = " + e.getResponseBodyAsString());
                ErrorsResponse errors = objectMapper.readValue(e.getResponseBodyAsString(), ErrorsResponse.class);
                YorcError error = errors.getErrors().iterator().next();
                throw YorcRestException.fromYorcError(error);
            }
        }
        catch (RestClientException e) {
            throw new Exception("An error occurred while calling " + httpMethod + " " + targetUrl, e);
        }
        return responseEntity;
    }


    public HttpEntity buildHttpEntityWithDefaultHeader(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Log the list of deployments known by Yorc
     */
    public void logDeployments() {
        try {
            Arrays.stream(callForEntity(providerConfiguration.getUrlYorc() + "/deployments", HttpMethod.GET, DeployInfosResponse[].class, buildHttpEntityWithDefaultHeader(new DeployInfosResponse())))
                    .forEach(item -> log.debug("Found a deployment in Yorc: " + item));
        }
        catch (Exception e) {
            log.warn("Cannot access Yorc: " + e.getCause());
        }
    }

    /**
     * Send a topology to Yorc
     * @param deploymentId
     * @return
     * @throws Exception
     */
    public String sendTopologyToYorc(String deploymentId) throws Exception {
        // Get file to upload
        final InputStream stream = new FileInputStream(new File("topology.zip"));
        final byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        stream.close();

        // Get specific headers
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        String targetUrl = providerConfiguration.getUrlYorc() + "/deployments";

        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.POST, request, String.class);
            if (response != null && response.getStatusCode() != null && !response.getStatusCode().toString().equals("Created")){
                throw new Exception("sendTopologyToYorc: Yorc returned an error : " + response.getStatusCode());
            }

            if (response != null && response.getHeaders() != null) {
                return response.getHeaders().getFirst("Location");
            }
        }
        catch(RestClientException rce) {
            throw new Exception("An error occurred while calling " + HttpMethod.POST + " " + targetUrl, rce);
        }

        return null;
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
        return null;
    }

    /**
     * Return the Deployment Status from Yorc
     * @param deploymentUrl
     * @return Status Look at yorc/deployments/structs.go to see all possible values
     * @throws Exception
     */
    public String getStatusFromYorc(String deploymentUrl) throws Exception {
        return null;
    }

    public DeployInfosResponse getDeploymentInfosFromYorc(String deploymentUrl) throws Exception {
        return null;
    }

    public NodeInfosResponse getNodesInfosFromYorc(String nodeInfoUrl) throws Exception {
        return null;
    }

    public InstanceInfosResponse getInstanceInfosFromYorc(String nodeInfoUrl) throws Exception {
        return null;
    }

    public AttributeResponse getAttributeFromYorc(String nodeInfoUrl) throws Exception {
        return null;
    }

    public LogResponse getLogFromYorc(int index) throws Exception {
        return callForEntity(providerConfiguration.getUrlYorc() + "/logs?index=" + index, HttpMethod.GET, LogResponse.class, buildHttpEntityWithDefaultHeader(new LogResponse()));
    }

    public EventResponse getEventFromYorc(int index) throws Exception {
        return callForEntity(providerConfiguration.getUrlYorc() + "/events?index=" + index, HttpMethod.GET, EventResponse.class, buildHttpEntityWithDefaultHeader(new LogEvent()));
    }

    public String undeploy(String deploymentUrl, boolean purge) throws Exception {
        return null;
    }

    public String stopTask(String taskUrl) throws Exception {
        return null;
    }

    public String postCustomCommandToYorc(String deploymentUrl, NodeOperationExecRequest request) throws Exception {
        return null;
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
        return null;
    }
}
