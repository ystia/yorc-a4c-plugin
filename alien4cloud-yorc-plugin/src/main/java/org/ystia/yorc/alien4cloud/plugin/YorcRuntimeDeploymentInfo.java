/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package org.ystia.yorc.alien4cloud.plugin;

import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;

import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
public class YorcRuntimeDeploymentInfo {
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

    // Last event received from Yorc
    private Event lastEvent;

    // TaskId of the current deployment
    private String deployTaskId;


}
