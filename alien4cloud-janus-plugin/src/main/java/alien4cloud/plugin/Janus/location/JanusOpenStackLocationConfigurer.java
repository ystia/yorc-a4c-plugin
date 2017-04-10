/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.location;

import java.util.List;
import java.util.Map;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ComputeContext;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService.ImageFlavorContext;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Configure resources for the OpenStack location type.
 */
@Slf4j
@Component
@Scope("prototype")
public class JanusOpenStackLocationConfigurer extends AbstractLocationConfigurer {
    private static final String IMAGE_ID_PROP = "image";
    private static final String FLAVOR_ID_PROP = "flavor";
    @Override
    protected String[] getLocationArchivePaths() {
        return new String[]{"commons/resources", "openstack/resources"};
    }

    @Override
    public List<String> getResourcesTypes() {
        return getAllResourcesTypes();
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
        ComputeContext computeContext = resourceGeneratorService
                .buildComputeContext("janus.nodes.openstack.Compute", null, JanusOpenStackLocationConfigurer.IMAGE_ID_PROP,
                        JanusOpenStackLocationConfigurer.FLAVOR_ID_PROP,
                resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, resourceAccessor);
    }
}
