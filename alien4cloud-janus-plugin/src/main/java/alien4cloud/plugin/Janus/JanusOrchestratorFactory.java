/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import alien4cloud.tosca.normative.ToscaType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.alien4cloud.tosca.model.definitions.PropertyConstraint;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.definitions.constraints.GreaterOrEqualConstraint;
import org.alien4cloud.tosca.model.definitions.constraints.PatternConstraint;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;

/**
 * Factory for Mock implementation of orchestrator instance.
 */
@Component("Janus-orchestrator-factory")
public class JanusOrchestratorFactory implements IOrchestratorPluginFactory<JanusOrchestrator, ProviderConfig> {
    public static final String OPENSTACK = "OpenStack";
    public static final String SLURM = "Slurm";
    private final Map<String, PropertyDefinition> deploymentProperties = Maps.newHashMap();
    @Resource
    private BeanFactory beanFactory;

    @Override
    public JanusOrchestrator newInstance() {
        return beanFactory.getBean(JanusOrchestrator.class);
    }

    @Override
    public void destroy(JanusOrchestrator instance) {
        // nothing specific, the plugin will be garbaged collected when all references are lost.
    }

    @Override
    public ProviderConfig getDefaultConfiguration() {
        return new ProviderConfig();
    }

    @Override
    public Class<ProviderConfig> getConfigurationType() {
        return ProviderConfig.class;
    }

    @Override
    public LocationSupport getLocationSupport() {
        return new LocationSupport(true, new String[]{OPENSTACK, SLURM});
    }

    @Override
    public ArtifactSupport getArtifactSupport() {
        // support all type of implementations artifacts
        return new ArtifactSupport(new String[]{"tosca.artifacts.Implementation", "tosca.artifacts.ShellScript"});
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return this.deploymentProperties;
    }

    //    @Override
    public String getType() {
        return "Janus Orchestrator";
    }

}