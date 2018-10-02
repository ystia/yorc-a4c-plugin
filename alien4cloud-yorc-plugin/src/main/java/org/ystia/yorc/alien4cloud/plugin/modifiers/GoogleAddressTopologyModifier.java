package org.ystia.yorc.alien4cloud.plugin.modifiers;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;

@Slf4j
@Component(value = GoogleAddressTopologyModifier.YORC_GOOGLE_ADDRESS_MODIFIER_TAG)
public class GoogleAddressTopologyModifier extends TopologyModifierSupport {
    public static final String YORC_GOOGLE_ADDRESS_MODIFIER_TAG = "yorc-google-address-modifier";

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

        Set<NodeTemplate> publicNetworksNodes = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.PublicNetwork", false);
        String assignableCap = "yorc.capabilities.Assignable";

        String addressTypeName = "yorc.nodes.google.Address";
        NodeType addressNodeType = toscaTypeSearchService.findMostRecent(NodeType.class, addressTypeName);
        Set<NodeTemplate> nodesToRemove = new HashSet<NodeTemplate>();
        List<AssignmentRelationship> relationshipsToAdd = new ArrayList<>();

        publicNetworksNodes.forEach(networkNodeTemplate -> {
            // For each Node Template requiring a connection to this Public
            // Network, creating a new Address Node Template
            for (NodeTemplate nodeTemplate : new ArrayList<>(topology.getNodeTemplates().values())) {
                final AbstractPropertyValue addresses = networkNodeTemplate.getProperties().get("addresses");
                final AbstractPropertyValue region = networkNodeTemplate.getProperties().get("region");

                if (nodeTemplate.getRelationships() == null) continue;

                nodeTemplate.getRelationships().forEach((rel, relationshipTemplate) -> {
                    if (relationshipTemplate.getTarget().equals(networkNodeTemplate.getName())) {
                        Map<String, AbstractPropertyValue> properties = new LinkedHashMap<>();
                        properties.put("addresses", addresses);
                        properties.put("region", region);



                        Map<String, Capability> capabilities = new LinkedHashMap<>();
                        Capability assignmentCap = new Capability();
                        assignmentCap.setType(assignableCap);
                        capabilities.put("assignment", assignmentCap);

                        if (addressNodeType == null) {
                            context.log().error("Node type with name <{}> cannot be found in the catalog.",
                                    addressTypeName);
                            return;
                        }

                        // Creating a new Address Node Template that will be
                        // associated to this Node Template requiring an assignment
                        String name = "address_" + nodeTemplate.getName();
                        NodeTemplate addressNodeTemplate = addNodeTemplate(
                                csar,
                                topology,
                                name,
                                addressNodeType.getElementId(),
                                addressNodeType.getArchiveVersion());

                        addressNodeTemplate.setProperties(properties);
                        addressNodeTemplate.setCapabilities(capabilities);

                        // Creating a new relationship between the Node template
                        // and the Google address node.
                        relationshipsToAdd.add(new AssignmentRelationship(
                                nodeTemplate, // source
                                addressNodeTemplate.getName(), // target
                                "assignment",
                                "assignment"));

                        context.log().info(
                                "<{}> created to provide a Google external address to <{}> on network <{}>",
                                name,
                                nodeTemplate.getName(),
                                networkNodeTemplate.getName());
                    }
                });
            }

            // Remove the public network node as replaced by Address node
            nodesToRemove.add(networkNodeTemplate);
            context.log().info(
                    "Public network <{}> removed as connectivity requirements are addressed by google address Node Templates",
                    networkNodeTemplate.getName());
            // Removing Public Network nodes for which a new Address Node
            // template was created
            nodesToRemove.forEach(pnn -> removeNode(topology, pnn));

            // Creating a relationship between each new Google Address Node Template
            // and the Source Node Template having an assignment requirement
            relationshipsToAdd.forEach( rel -> addRelationshipTemplate(
                    csar,
                    topology,
                    rel.sourceNode,
                    rel.targetNodeName,
                    "yorc.relationships.AssignsTo",
                    rel.requirementName,
                    rel.targetCapabilityName));
        });

    }

    @AllArgsConstructor
    private class AssignmentRelationship {
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }
}
