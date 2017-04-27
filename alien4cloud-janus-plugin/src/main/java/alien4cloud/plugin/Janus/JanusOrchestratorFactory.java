/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import java.util.Map;

import javax.annotation.Resource;

import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import com.google.common.collect.Maps;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for Mock implementation of orchestrator instance.
 */
@Component("Janus-orchestrator-factory")
public class JanusOrchestratorFactory implements IOrchestratorPluginFactory<JanusOrchestrator, ProviderConfig> {
    public static final String OPENSTACK = "OpenStack";
    public static final String SLURM = "Slurm";
    public static final String KUBERNETES = "Kubernetes";
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
        return new LocationSupport(true, new String[]{OPENSTACK, SLURM, KUBERNETES});
    }

    @Override
    public ArtifactSupport getArtifactSupport() {
        // support all type of implementations artifacts
        return new ArtifactSupport(new String[]{"tosca.artifacts.Implementation.Python",
                "tosca.artifacts.Implementation.Bash", "tosca.artifacts.Implementation.Ansible", "tosca.artifacts.Deployment.Image.Container.Kubernetes"});
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
