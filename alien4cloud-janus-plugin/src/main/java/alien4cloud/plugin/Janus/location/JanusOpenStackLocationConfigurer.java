/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.location;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ComputeContext;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ImageFlavorContext;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.tosca.parser.ParsingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Configure resources for the OpenStack location type.
 */
@Slf4j
@Component
@Scope("prototype")
public class JanusOpenStackLocationConfigurer extends AbstractLocationConfigurer {

    @Override
    protected List<PluginArchive> parseArchives() throws ParsingException {
        List<PluginArchive> archives = Lists.newArrayList();
        addToAchive(archives, "openstack/openstack-resources");
        addToAchive(archives, "openstack/resources");
        return archives;
    }

    @Override
    public List<String> getResourcesTypes() {
        return Lists.newArrayList("janus.nodes.openstack.Image", "janus.nodes.openstack.Flavor", "janus.nodes.openstack.Compute",
                "janus.nodes.openstack.BlockStorage", "janus.nodes.openstack.Network");
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        return getMatchingConfigurations("openstack/resources-matching-config.yml");
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
        ImageFlavorContext imageContext = resourceGeneratorService.buildContext("janus.nodes.openstack.Image", "id", resourceAccessor);
        ImageFlavorContext flavorContext = resourceGeneratorService.buildContext("janus.nodes.openstack.Flavor", "id", resourceAccessor);
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
        ComputeContext computeContext = resourceGeneratorService.buildComputeContext("janus.nodes.openstack.Compute", null, IMAGE_ID_PROP, FLAVOR_ID_PROP,
                resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, resourceAccessor);
    }
}
