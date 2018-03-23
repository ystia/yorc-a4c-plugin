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
package org.ystia.yorc.alien4cloud.plugin.modifiers;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.model.orchestrators.locations.LocationModifierReference;
import alien4cloud.orchestrators.locations.events.AfterLocationCreated;
import alien4cloud.orchestrators.locations.services.LocationModifierService;
import alien4cloud.plugin.model.ManagedPlugin;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.modifiers.FlowPhases;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.ystia.yorc.alien4cloud.plugin.YstiaOrchestratorFactory;


/**
 * A {@code LocationCreationListener} is used to register internal modifiers when a location is created.
 *
 * @author Loic Albertin
 */
@Component
@Slf4j
public class LocationCreationListener implements ApplicationListener<AfterLocationCreated> {


    @Inject
    private ManagedPlugin selfContext;

    @Resource
    private LocationModifierService locationModifierService;

    private LocationModifierReference openstackFipModifierRef;
    private LocationModifierReference openstackBSWFModifierRef;

    @PostConstruct
    public synchronized void init() {
        openstackFipModifierRef = new LocationModifierReference();
        openstackFipModifierRef.setPluginId(selfContext.getPlugin().getId());
        openstackFipModifierRef.setBeanName(FipTopologyModifier.YORC_OPENSTACK_FIP_MODIFIER_TAG);
        openstackFipModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);
        openstackBSWFModifierRef = new LocationModifierReference();
        openstackBSWFModifierRef.setPluginId(selfContext.getPlugin().getId());
        openstackBSWFModifierRef.setBeanName(OpenStackBSComputeWFModifier.YORC_OPENSTACK_BS_WF_MODIFIER_TAG);
        openstackBSWFModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);
    }


    @Override
    public void onApplicationEvent(AfterLocationCreated event) {
       log.debug("Got location creation event for infrastructure type {}", event.getLocation().getInfrastructureType());
       if (YstiaOrchestratorFactory.OPENSTACK.equals(event.getLocation().getInfrastructureType())) {
           locationModifierService.add(event.getLocation(), openstackFipModifierRef);
           locationModifierService.add(event.getLocation(), openstackBSWFModifierRef);
       }
    }
}
