/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.model.components.PropertyConstraint;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.constraints.GreaterOrEqualConstraint;
import alien4cloud.model.components.constraints.PatternConstraint;
import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;
import alien4cloud.tosca.normative.ToscaType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
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

        // Field 1 : managerUrl as string
        PropertyDefinition managerUrl = new PropertyDefinition();
        managerUrl.setType(ToscaType.STRING);
        managerUrl.setDefault("http://localhost:4242");
        managerUrl.setRequired(false);
        managerUrl.setDescription("PaaS manager URL");
        managerUrl.setConstraints(null);
        PatternConstraint manageUrlConstraint = new PatternConstraint();
        manageUrlConstraint.setPattern("http://.+");
        managerUrl.setConstraints(Collections.singletonList((PropertyConstraint) manageUrlConstraint));

        // Field 2 : number backup with constraint
        PropertyDefinition numberBackup = new PropertyDefinition();
        numberBackup.setType(ToscaType.INTEGER);
        numberBackup.setDefault("0606060606");
        numberBackup.setRequired(false);
        numberBackup.setDescription("Number of backup");
        numberBackup.setConstraints(null);
        GreaterOrEqualConstraint greaterOrEqualConstraint = new GreaterOrEqualConstraint();
        greaterOrEqualConstraint.setGreaterOrEqual(String.valueOf("1"));
        numberBackup.setConstraints(Lists.newArrayList((PropertyConstraint) greaterOrEqualConstraint));

        // Field 3 : email manager
        PropertyDefinition managerEmail = new PropertyDefinition();
        managerEmail.setType(ToscaType.STRING);
        managerEmail.setDefault("xBD@yopmail.com");
        managerEmail.setRequired(false);
        managerEmail.setDescription("PaaS manager email");
        managerEmail.setConstraints(null);
        PatternConstraint managerEmailConstraint = new PatternConstraint();
        managerEmailConstraint.setPattern(".+@.+");
        managerEmail.setConstraints(Collections.singletonList((PropertyConstraint) managerEmailConstraint));

        deploymentProperties.put("managementUrl", managerUrl);
        deploymentProperties.put("numberBackup", numberBackup);
        deploymentProperties.put("managerEmail", managerEmail);

        return deploymentProperties;
    }

    //    @Override
    public String getType() {
        return "Janus Orchestrator";
    }

}