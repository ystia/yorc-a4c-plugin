/**
 * Created by a628490 on 11/07/2016.
 */
/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.location;

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

    public Map<String, MatchingConfiguration> getMatchingConfigurations(String matchingConfigRelativePath) {
        Path matchingConfigPath = selfContext.getPluginPath().resolve(matchingConfigRelativePath);
        MatchingConfigurations matchingConfigurations = null;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch (ParsingException e) {
            return Maps.newHashMap();
        }
        return matchingConfigurations.getMatchingConfigurations();
    }
}
