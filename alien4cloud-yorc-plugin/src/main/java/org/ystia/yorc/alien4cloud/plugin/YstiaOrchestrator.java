/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package org.ystia.yorc.alien4cloud.plugin;

import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.ystia.yorc.alien4cloud.plugin.location.AbstractLocationConfigurerFactory;
import org.ystia.yorc.alien4cloud.plugin.service.PluginArchiveService;

import javax.inject.Inject;
import java.util.List;

/**
 * Yorc implementation for an orchestrator instance.
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class YstiaOrchestrator extends YorcPaaSProvider {
    @Inject
    private AbstractLocationConfigurerFactory yorcLocationConfigurerFactory;

    @Inject
    private PluginArchiveService archiveService;

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return yorcLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        List<PluginArchive> archives = Lists.newArrayList();
        archives.add(archiveService.parsePluginArchives("commons/resources"));
        archives.add(archiveService.parsePluginArchives("docker/resources"));

        return archives;
    }
}