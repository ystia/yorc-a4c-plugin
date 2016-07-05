/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

@Getter
@Setter
@AllArgsConstructor
public class JanusRuntimeDeploymentInfo {
    private PaaSTopologyDeploymentContext deploymentContext;
    private DeploymentStatus status;
    /**
     * Represents the status of every instance of node templates currently deployed.
     * 
     * NodeTemplateId -> InstanceId -> InstanceInformation
     */
    private Map<String, Map<String, InstanceInformation>> instanceInformations;

}
