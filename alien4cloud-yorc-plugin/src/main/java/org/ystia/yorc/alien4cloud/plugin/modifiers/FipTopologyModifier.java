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

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * Modifies an OpenStack topology to add a new Floating IP Node template
 * for each Node template requiring a connection to a Public Network.
 */
@Slf4j
@Component(value = FipTopologyModifier.YORC_OPENSTACK_FIP_MODIFIER_TAG)
public class FipTopologyModifier extends TopologyModifierSupport {

    public static final String YORC_OPENSTACK_FIP_MODIFIER_TAG = "yorc-openstack-fip-modifier";

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.debug("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {

        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        Set<NodeTemplate> publicNetworksNodes = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.openstack.PublicNetwork", false);

        String fipConnectivityCap = "yorc.capabilities.openstack.FIPConnectivity";
        String fipNodeType = "yorc.nodes.openstack.FloatingIP";
        NodeType fipType = toscaTypeSearchService.findMostRecent(NodeType.class, fipNodeType);
        Set<NodeTemplate> nodesToRemove = new HashSet<NodeTemplate>();
        List<NetworkRelationshipConfig> relationshipsToAdd = new ArrayList<NetworkRelationshipConfig>();

        publicNetworksNodes.forEach(networkNodeTemplate -> {
            final AbstractPropertyValue networkName = networkNodeTemplate.getProperties().get("floating_network_name");

            // For each Node Template requiring a connection to this Public 
            // Network, creating a new Floating IP Node Template
            for (NodeTemplate nodeTemplate : new ArrayList<>(topology.getNodeTemplates().values())) {

                if (nodeTemplate.getRelationships() == null) continue;

                nodeTemplate.getRelationships().forEach((rel, relationshipTemplate) -> {

                    if (relationshipTemplate.getTarget().equals(networkNodeTemplate.getName())) {

                        Map<String, AbstractPropertyValue> properties = new LinkedHashMap<>();
                        properties.put("floating_network_name", networkName);
                        
                        Map<String, Capability> capabilities = new LinkedHashMap<>();
                        Capability connectionCap = new Capability();
                        connectionCap.setType(fipConnectivityCap);
                        capabilities.put("connection", connectionCap);

                        if (fipType == null) {
                            context.log().error("Node type with name <{}> cannot be found in the catalog.",
                            fipNodeType);
                            return;
                        }

                        // Creating a new Floating IP Node Template that will be
                        // associated to this Node Template requiring a 
                        // connection to the Public Network
                        String fipName = "FIP" + nodeTemplate.getName();
                        NodeTemplate fipNodeTemplate = addNodeTemplate(
                            csar,
                            topology,
                            fipName,
                            fipType.getElementId(),
                            fipType.getArchiveVersion());

                        fipNodeTemplate.setProperties(properties);
                        fipNodeTemplate.setCapabilities(capabilities);

                        // Creating a new relationship between the Node template
                        // and the Floating IP node.
                        // Not attempting to re-use/modify the relationship
                        // existing between the Node Template and the Public
                        // Network, as once the Public Network will be removed,
                        // all related relationhips will be removed.
                        // The new relationship will be created outside of the
                        // foreach loops, as its creation modifies elements on
                        // which these loops are iterating.
                        relationshipsToAdd.add(new NetworkRelationshipConfig(
                            nodeTemplate, // source
                            fipNodeTemplate.getName(), // target
                            relationshipTemplate.getRequirementName(),
                            relationshipTemplate.getTargetedCapabilityName()));

                        context.log().info(
                            "<{}> created to provide a Floating IP address to <{}> on network <{}>",
                            fipName,
                            nodeTemplate.getName(),
                            networkNodeTemplate.getName());
                    }
                });
            }

            // Now that Floating IP nodes have been created to provide the
            // required connectivity, removing this public network node
            nodesToRemove.add(networkNodeTemplate);
            context.log().info(
                "Public network <{}> removed as connectivity requirements are addressed by Floating IP Node Templates",
                networkNodeTemplate.getName());
        });

        // Removing Public Network nodes for which a new Floating IP Node 
        // template was created
        nodesToRemove.forEach(pnn -> removeNode(topology, pnn));

        // Creating a relationship between each new Floating IP Node Template
        // and the Source Node Template having a connectivity requirement  
        relationshipsToAdd.forEach( rel -> addRelationshipTemplate(
            csar,
            topology,
            rel.sourceNode,
            rel.targetNodeName,
            NormativeRelationshipConstants.NETWORK,
            rel.requirementName,
            rel.targetCapabilityName));
    }

    @AllArgsConstructor
    private class NetworkRelationshipConfig {
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }

}
