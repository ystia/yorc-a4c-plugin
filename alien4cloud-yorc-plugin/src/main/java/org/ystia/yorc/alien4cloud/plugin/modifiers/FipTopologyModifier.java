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
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * Modifies an OpenStack topology, replacing each Public Network Node having
 * connectivity requirements from other Nodes, by a Floating IP Node.
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

        publicNetworksNodes.forEach(networkNodeTemplate -> {

            for (NodeTemplate nodeTemplate : new ArrayList<>(topology.getNodeTemplates().values())) {

                if (nodeTemplate.getRelationships() == null) continue;

                nodeTemplate.getRelationships().forEach((rel, relationshipTemplate) -> {

                    if (relationshipTemplate.getTarget().equals(networkNodeTemplate.getName())) {

                        Map<String, Capability> capabilities = new LinkedHashMap<>();
                        Capability connectionCap = new Capability();
                        connectionCap.setType(fipConnectivityCap);
                        capabilities.put("connection", connectionCap);

                        if (fipType == null) {
                            context.log().error("Node type with name <{}> cannot be found in the catalog.",
                            fipNodeType);
                            return;
                        }

                        NodeTemplate nt = replaceNode(
                            csar,
                            topology,
                            networkNodeTemplate,
                            fipType.getElementId(),
                            fipType.getArchiveVersion());

                        nt.setCapabilities(capabilities);
                        relationshipTemplate.setRequirementType(fipConnectivityCap);

                        context.log().info(
                            "Template <{}> will be modified to be a Floating IP provider for <{}>.",
                            networkNodeTemplate.getName(),
                            nodeTemplate.getName());
                    }
                });
            }
        });
    }
}
