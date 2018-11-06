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
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;

@Slf4j
@Component(value = GooglePrivateNetworkTopologyModifier.YORC_GOOGLE_PRIVATE_NETWORK_MODIFIER_TAG)
public class GooglePrivateNetworkTopologyModifier extends TopologyModifierSupport {

    public static final String YORC_GOOGLE_PRIVATE_NETWORK_MODIFIER_TAG = "yorc-google-private-network-modifier";

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

        String subnetType = "yorc.nodes.google.Subnetwork";
        NodeType subnetNode = toscaTypeSearchService.findMostRecent(NodeType.class, subnetType);
        if (subnetNode == null) {
            context.log().error("Node type with name <{}> cannot be found in the catalog.",
                    subnetType);
            return;
        }

        Set<NodeTemplate> privateNetworksNodes = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.PrivateNetwork", false);
        List<RelationshipCreation> relationshipsToAdd = new ArrayList<>();
        List<RelationshipRemoval> relationshipsToRemove = new ArrayList<>();

        privateNetworksNodes.forEach(privNetNodeTemplate -> {
            // For each Node Template requiring a connection to this private network
            // A new connection is created to the required subnet instead
            for (NodeTemplate nodeTemplate : new ArrayList<>(topology.getNodeTemplates().values())) {
                if (nodeTemplate.getRelationships() == null) continue;

                nodeTemplate.getRelationships().forEach((rel, relationshipTemplate) -> {
                    if (relationshipTemplate.getTarget().equals(privNetNodeTemplate.getName())) {
                        String subnet = "";
                        // check if subnet property is defined
                        if (relationshipTemplate.getType().equals("yorc.relationships.google.Network")
                                && relationshipTemplate.getProperties().containsKey("subnet")) {
                            final AbstractPropertyValue subnetVal = relationshipTemplate.getProperties().get("subnet");
                            if (subnetVal != null && subnetVal instanceof ScalarPropertyValue) {
                                subnet = ((ScalarPropertyValue) subnetVal).getValue();
                            }
                        }

                        if (subnet.isEmpty()) {
                            String zone = ((ScalarPropertyValue) nodeTemplate.getProperties().get("zone")).getValue();
                            String region = GoogleAddressTopologyModifier.extractRegionFromZone(zone);
                            subnet = retrieveFirstRegionalMatchingSubnet(privNetNodeTemplate, region);
                        }

                        if (subnet.isEmpty()) {
                            context.log().error("No matching subnet found for network <{}> with node <{}>.",
                                    privNetNodeTemplate.getName(), nodeTemplate.getName());
                            return;
                        }

                        addSubnetNodes(privNetNodeTemplate, nodeTemplate, subnet, csar, topology, subnetNode, relationshipsToAdd);

                        // Remove this relationship
                        relationshipsToRemove.add(new RelationshipRemoval(relationshipTemplate.getName(), nodeTemplate.getName()));
                    }
                });
            }
        });

        relationshipsToAdd.forEach( rel -> addRelationshipTemplate(
                csar,
                topology,
                rel.sourceNode,
                rel.targetNodeName,
                rel.name,
                rel.requirementName, rel.targetCapabilityName));

        relationshipsToRemove.forEach( rel ->
                removeRelationship(csar, topology, rel.sourceNodeName, rel.name));
    }

    @AllArgsConstructor
    private class RelationshipCreation {
        private String name;
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }

    @AllArgsConstructor
    private class RelationshipRemoval {
        private String name;
        private String sourceNodeName;
    }

    /**
     * Retrieves the first regional matching subnet for defined network
     * @param networkNode
     * @param requiredRegion
     * @return String the subnet name
     */
    private String retrieveFirstRegionalMatchingSubnet(final NodeTemplate networkNode, final String requiredRegion) {
        String ret = "";
        final AbstractPropertyValue subnetPropVal = networkNode.getProperties().get("custom_subnetworks");
        if (subnetPropVal != null && subnetPropVal instanceof ListPropertyValue) {
            ListPropertyValue subnets = (ListPropertyValue) subnetPropVal;
            List<Object> subnetsList = subnets.getValue();
            for (Object subnet : subnetsList) {
                Map<String, Object> props = (LinkedHashMap<String, Object>) subnet;
                String region = (String) props.get("region");
                if (region.equals(requiredRegion)) {
                    ret = (String) props.get("name");
                    break;
                }
            }
        }
        return ret;
    }

    /***
     * This allows to create subnet node template for each subnet
     * It adds dependency relationship btw network and subnet
     * It adds network relationship for the rquired subnet with node
     * @param networkNode
     * @param node
     * @param subnetName
     * @param csar
     * @param topology
     * @param subnetNode
     * @param relationshipsToAdd
     */
    private void addSubnetNodes(final NodeTemplate networkNode, final NodeTemplate node, final String subnetName, final Csar csar, final Topology topology, final NodeType subnetNode, final List<RelationshipCreation> relationshipsToAdd) {
        final AbstractPropertyValue subnetPropVal = networkNode.getProperties().get("custom_subnetworks");
        if (subnetPropVal != null && subnetPropVal instanceof ListPropertyValue) {
            ListPropertyValue subnets = (ListPropertyValue) subnetPropVal;
            List<Object> subnetsList = subnets.getValue();
            for (Object subnet : subnetsList) {
                Map<String, Object> props = (LinkedHashMap<String, Object>) subnet;
                String name = (String) props.get("name");
                // Copy props
                Map<String, AbstractPropertyValue> newProps = new LinkedHashMap<>();
                props.forEach((k, v) -> {
                    if (v instanceof String) {
                        newProps.put(k, new ScalarPropertyValue((String) v));
                    } else if (v instanceof ArrayList) {
                        newProps.put(k, new ListPropertyValue((ArrayList) v));
                    }
                });
                String subnetNodename = name.replace("-", "_");
                NodeTemplate subnetNodeTemplate = addNodeTemplate(
                        csar,
                        topology,
                        subnetNodename,
                        subnetNode.getElementId(),
                        subnetNode.getArchiveVersion());

                subnetNodeTemplate.setProperties(newProps);
                // Creating a new dependency relationship between the Network and its sub-network
                relationshipsToAdd.add(new RelationshipCreation(
                        "tosca.relationships.DependsOn",
                        subnetNodeTemplate, // source
                        networkNode.getName(), // target
                        "dependency", "feature"));

                // Create network relationship for specific required sub-network
                if (subnetName.equals(name)) {
                    // Creating a new network relationship between the sub-network and the node
                    relationshipsToAdd.add(new RelationshipCreation(
                            "yorc.relationships.google.Network",
                            node, // source
                            subnetNodename, // target
                            "network", "connection"));
                }
            }
        }
    }

}
