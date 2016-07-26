/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.rest;

import org.apache.commons.compress.utils.IOUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;

import java.io.*;

/**
 * Created by a628490 on 22/07/2016.
 */
public class RestClient {

    public void postTopologyToJanusTEST1() throws FileNotFoundException {
        final InputStream fis = new FileInputStream(new File("")); // or whatever
        final RequestCallback requestCallback = new RequestCallback() {
            @Override
            public void doWithRequest(final ClientHttpRequest request) throws IOException {
                request.getHeaders().add("Content-type", "application/zip");
                IOUtils.copy(fis, request.getBody());
            }
        };
        final RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setBufferRequestBody(false);
        restTemplate.setRequestFactory(requestFactory);
        final HttpMessageConverterExtractor<String> responseExtractor =
                new HttpMessageConverterExtractor<String>(String.class, restTemplate.getMessageConverters());
        restTemplate.execute("http://localhost:8800/deployments", HttpMethod.POST, requestCallback, responseExtractor);
    }

    public void postTopologyToJanus() {
        // Set the Content-Type header
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(new MediaType("application","zip"));
        HttpEntity<String> requestEntity = new HttpEntity<>("topology.zip", requestHeaders);

// Create a new RestTemplate instance
        RestTemplate restTemplate = new RestTemplate();

// Add the Jackson and String message converters
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        restTemplate.getMessageConverters().add(new StringHttpMessageConverter());

// Make the HTTP POST request, marshaling the request to JSON, and the response to a String
        ResponseEntity<InputStream> responseEntity = restTemplate.exchange("http://10.0.0.4:8800/deployments", HttpMethod.POST, requestEntity, InputStream.class);
        InputStream result = responseEntity.getBody();
    }
}
