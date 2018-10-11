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

import alien4cloud.model.orchestrators.Orchestrator;
import alien4cloud.model.orchestrators.locations.LocationModifierReference;
import alien4cloud.orchestrators.locations.events.AfterLocationCreated;
import alien4cloud.orchestrators.locations.services.LocationModifierService;
import alien4cloud.orchestrators.services.OrchestratorService;
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

    @Resource
    private OrchestratorService orchestratorService;

    private LocationModifierReference openstackFipModifierRef;
    private LocationModifierReference openstackBSWFModifierRef;
    private LocationModifierReference wfOperationHostModifierRef;
    private LocationModifierReference serviceTopologyModifierRef;
    private LocationModifierReference kubernetesTopologyModifierRef;
    private LocationModifierReference yorcKubernetesTopologyModifierRef;
    private LocationModifierReference googleAddressModifierRef;

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
        wfOperationHostModifierRef = new LocationModifierReference();
        wfOperationHostModifierRef.setPluginId(selfContext.getPlugin().getId());
        wfOperationHostModifierRef.setBeanName(OperationHostModifier.YORC_WF_OPERATION_HOST_MODIFIER_TAG);
        wfOperationHostModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        serviceTopologyModifierRef = new LocationModifierReference();
        serviceTopologyModifierRef.setPluginId(selfContext.getPlugin().getId());
        serviceTopologyModifierRef.setBeanName(ServiceTopologyModifier.YORC_SERVICE_TOPOLOGY_MODIFIER_TAG);
        serviceTopologyModifierRef.setPhase(FlowPhases.POST_MATCHED_NODE_SETUP);

        yorcKubernetesTopologyModifierRef = new LocationModifierReference();
        yorcKubernetesTopologyModifierRef.setPluginId(selfContext.getPlugin().getId());
        yorcKubernetesTopologyModifierRef.setBeanName(KubernetesTopologyModifier.YORC_KUBERNETES_MODIFIER_TAG);
        yorcKubernetesTopologyModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);

        kubernetesTopologyModifierRef = new LocationModifierReference();
        kubernetesTopologyModifierRef.setPluginId("alien4cloud-kubernetes-plugin");
        kubernetesTopologyModifierRef.setBeanName("kubernetes-modifier");
        kubernetesTopologyModifierRef.setPhase(FlowPhases.POST_LOCATION_MATCH);

        googleAddressModifierRef = new LocationModifierReference();
        googleAddressModifierRef.setPluginId(selfContext.getPlugin().getId());
        googleAddressModifierRef.setBeanName(GoogleAddressTopologyModifier.YORC_GOOGLE_ADDRESS_MODIFIER_TAG);
        googleAddressModifierRef.setPhase(FlowPhases.POST_NODE_MATCH);
    }


    @Override
    public void onApplicationEvent(AfterLocationCreated event) {
        log.debug("Got location creation event for infrastructure type {}", event.getLocation().getInfrastructureType());

        Orchestrator orchestrator = orchestratorService.getOrFail(event.getLocation().getOrchestratorId());

        if (orchestrator.getPluginId().equals(selfContext.getPlugin().getId())) {
            if (YstiaOrchestratorFactory.OPENSTACK.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), openstackFipModifierRef);
                locationModifierService.add(event.getLocation(), openstackBSWFModifierRef);
            } else if (YstiaOrchestratorFactory.KUBERNETES.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), yorcKubernetesTopologyModifierRef);
                locationModifierService.add(event.getLocation(), kubernetesTopologyModifierRef);
            } else if (YstiaOrchestratorFactory.GOOGLE.equals(event.getLocation().getInfrastructureType())) {
                locationModifierService.add(event.getLocation(), googleAddressModifierRef);
            }
            locationModifierService.add(event.getLocation(), wfOperationHostModifierRef);
            locationModifierService.add(event.getLocation(), serviceTopologyModifierRef);
        }
    }
}
