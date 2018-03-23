/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin.location;

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
public class YorcOpenStackLocationConfigurer extends AbstractLocationConfigurer {
    private static final String IMAGE_ID_PROP = "image";
    private static final String FLAVOR_ID_PROP = "flavor";
    @Override
    protected String[] getLocationArchivePaths() {
        return new String[]{"openstack/resources"};
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
        ImageFlavorContext imageContext = resourceGeneratorService.buildContext("yorc.nodes.openstack.Image", "id", resourceAccessor);
        ImageFlavorContext flavorContext = resourceGeneratorService.buildContext("yorc.nodes.openstack.Flavor", "id", resourceAccessor);
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
                .buildComputeContext("yorc.nodes.openstack.Compute", null, YorcOpenStackLocationConfigurer.IMAGE_ID_PROP,
                        YorcOpenStackLocationConfigurer.FLAVOR_ID_PROP,
                resourceAccessor);

        return resourceGeneratorService.generateComputeFromImageAndFlavor(imageContext, flavorContext, computeContext, null, resourceAccessor);
    }
}
