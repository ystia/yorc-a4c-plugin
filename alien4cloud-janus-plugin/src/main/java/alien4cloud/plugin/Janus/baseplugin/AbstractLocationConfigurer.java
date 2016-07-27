/**
 * Created by a628490 on 11/07/2016.
 */
/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.baseplugin;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ComputeContext;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ImageFlavorContext;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.exception.PluginParseException;
import alien4cloud.plugin.PluginManager;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.tosca.ArchiveParser;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Configure resources for the location type.
 */
@Slf4j
@Component
@Scope("prototype")
public abstract class AbstractLocationConfigurer implements ILocationConfiguratorPlugin {
    @Inject
    protected ArchiveParser archiveParser;
    @Inject
    protected MatchingConfigurationsParser matchingConfigurationsParser;
    @Inject
    protected PluginManager pluginManager;
    @Inject
    protected ManagedPlugin selfContext;
    @Inject
    protected LocationResourceGeneratorService resourceGeneratorService;

    protected List<PluginArchive> archives;

    protected static final String IMAGE_ID_PROP = "imageId";
    protected static final String FLAVOR_ID_PROP = "flavorId";

    @Override
    public List<PluginArchive> pluginArchives() throws PluginParseException {
        if (archives == null) {
            try {
                archives = parseArchives();
            } catch (ParsingException e) {
                log.error(e.getMessage());
                throw  new PluginParseException(e.getMessage());
            }
        }
        return archives;
    }

    protected List<PluginArchive> parseArchives() throws ParsingException {
        List<PluginArchive> archives = Lists.newArrayList();
        addToAchive(archives, "slurm/slurm-resources");
        addToAchive(archives, "slurm/resources");
        return archives;
    }

    protected void addToAchive(List<PluginArchive> archives, String path) throws ParsingException {
        Path archivePath = selfContext.getPluginPath().resolve(path);
        // Parse the archives
        ParsingResult<ArchiveRoot> result = archiveParser.parseDir(archivePath);
        PluginArchive pluginArchive = new PluginArchive(result.getResult(), archivePath);
        archives.add(pluginArchive);
    }

    @Override
    public List<String> getResourcesTypes() {
        return Lists.newArrayList("janus.nodes.slurm.Image", "janus.nodes.slurm.Flavor", "janus.nodes.slurm.Compute",
                "janus.nodes.slurm.BlockStorage", "janus.nodes.slurm.Network");
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        Path matchingConfigPath = selfContext.getPluginPath().resolve("slurm/resources-matching-config.yml");
        MatchingConfigurations matchingConfigurations = null;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch (ParsingException e) {
            return Maps.newHashMap();
        }
        return matchingConfigurations.getMatchingConfigurations();
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
        ImageFlavorContext imageContext = resourceGeneratorService.buildContext("janus.nodes.slurm.Image", "id", resourceAccessor);
        ImageFlavorContext flavorContext = resourceGeneratorService.buildContext("janus.nodes.slurm.Flavor", "id", resourceAccessor);
        boolean canProceed = true;

        if (CollectionUtils.isEmpty(imageContext.getTemplates())) {
            log.warn("At least one configured image resource is required for the auto-configuration");
            canProceed = false;
        }
        if (CollectionUtils.isEmpty(flavorContext.getTemplates())) {
            log.warn("At least one configured flavor resource is required for the auto-configuration");
            canProceed = false;
        }
        if (!canProceed) {
            log.warn("Skipping auto configuration");
            return null;
        }
        ComputeContext computeContext = resourceGeneratorService.buildComputeContext("janus.nodes.slurm.Compute", null, IMAGE_ID_PROP, FLAVOR_ID_PROP,
                resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, resourceAccessor);
    }
}
