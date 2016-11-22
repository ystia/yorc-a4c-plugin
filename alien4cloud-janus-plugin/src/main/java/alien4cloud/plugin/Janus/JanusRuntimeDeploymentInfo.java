/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
@Setter
@RequiredArgsConstructor
public class JanusRuntimeDeploymentInfo {
    @NonNull
    private PaaSTopologyDeploymentContext deploymentContext;
    @NonNull
    private DeploymentStatus status;
    /**
     * Represents the status of every instance of node templates currently deployed.
     * <p>
     * NodeTemplateId -> InstanceId -> InstanceInformation
     */
    @NonNull
    private Map<String, Map<String, InstanceInformation>> instanceInformations;

    @NonNull
    private String deploymentUrl;

    // Used to execute event check thread
    private ExecutorService executor = Executors.newFixedThreadPool(2);

}
