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
                        String zone;
                        String region = "";
                        // check if subnet property is defined
                        if (relationshipTemplate.getType().equals("yorc.relationships.google.Network")
                                && relationshipTemplate.getProperties().containsKey("subnet")) {
                            final AbstractPropertyValue subnetVal = relationshipTemplate.getProperties().get("subnet");
                            if (subnetVal != null && subnetVal instanceof ScalarPropertyValue) {
                                subnet = ((ScalarPropertyValue) subnetVal).getValue();
                            }
                        }

                        if (subnet.isEmpty()) {
                            zone = ((ScalarPropertyValue) nodeTemplate.getProperties().get("zone")).getValue();
                            region = GoogleAddressTopologyModifier.extractRegionFromZone(zone);
                            subnet = retrieveFirstRegionalMatchingSubnet(privNetNodeTemplate, region);
                        }

                        if (subnet.isEmpty()) {
                            String autoCreateModeStr = ((ScalarPropertyValue) privNetNodeTemplate.getProperties().get("auto_create_subnetworks")).getValue();
                            String cidr = ((ScalarPropertyValue) privNetNodeTemplate.getProperties().get("cidr")).getValue();
                            boolean autoCreateMode = Boolean.parseBoolean(autoCreateModeStr);
                            if (!autoCreateMode && cidr.isEmpty()) {
                                context.log().error("No matching subnet found for network <{}> with node <{}>.",
                                        privNetNodeTemplate.getName(), nodeTemplate.getName());
                                return;
                            } else if (!cidr.isEmpty()) {
                                // Retrieve the cidr to create default subnet in the node region (only one subnet can be created with unique ip/range)
                                if (!canUseDefaultSubnet(csar, topology, nodeTemplate, subnetNode, cidr, region, privNetNodeTemplate.getName(), relationshipsToAdd)) {
                                    context.log().error("No matching subnet found for network <{}> with node <{}>.",
                                            privNetNodeTemplate.getName(), nodeTemplate.getName());
                                    return;
                                }
                            }
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
                String subA4CName = buildSubnetNodeName(networkNode.getName(), name);
                Optional<NodeTemplate> optional = checkSubnetExists(topology, subA4CName);
                if (!optional.isPresent()) {
                    NodeTemplate subnetNodeTemplate = addNodeTemplate(
                            csar,
                            topology,
                            subA4CName,
                            subnetNode.getElementId(),
                            subnetNode.getArchiveVersion());

                    // Copy props
                    Map<String, AbstractPropertyValue> newProps = new LinkedHashMap<>();
                    props.forEach((k, v) -> {
                        if (v instanceof String) {
                            newProps.put(k, new ScalarPropertyValue((String) v));
                        } else if (v instanceof ArrayList) {
                            newProps.put(k, new ListPropertyValue((ArrayList) v));
                        }
                    });
                    subnetNodeTemplate.setProperties(newProps);
                    // Creating a new dependency relationship between the Network and its sub-network
                    relationshipsToAdd.add(new RelationshipCreation(
                            "tosca.relationships.DependsOn",
                            subnetNodeTemplate, // source
                            networkNode.getName(), // target
                            "dependency", "feature"));
                }

                // Create network relationship for specific required sub-network
                if (!subnetName.isEmpty() && subnetName.equals(name)) {
                    // Creating a new network relationship between the sub-network and the node
                    relationshipsToAdd.add(new RelationshipCreation(
                            "yorc.relationships.google.Network",
                            node, // source
                            subA4CName, // target
                            "network", "connection"));
                }
            }
        }
    }

    /**
     * Create if not exist a default subnet from cidr tosca.nodes.Network property
     * Create new relationship btw this network and the default subnet
     * Create new relationship btw node and subnet
     * Returns false if default subnet already exists and has different region than node
     * @param csar
     * @param topology
     * @param node
     * @param subnetNode
     * @param cidr
     * @param region
     * @param networkNodeName
     * @param relationshipsToAdd
     * @return
     */
    private boolean canUseDefaultSubnet(final Csar csar, final Topology topology, final NodeTemplate node, final NodeType subnetNode, final String cidr, final String region, final String networkNodeName, final List<RelationshipCreation> relationshipsToAdd) {
        String subA4CName = buildSubnetNodeName(networkNodeName, "default");
        Optional<NodeTemplate> optional = checkSubnetExists(topology, subA4CName);
        if (!optional.isPresent()) {
            NodeTemplate subnetNodeTemplate = addNodeTemplate(
                    csar,
                    topology,
                    subA4CName,
                    subnetNode.getElementId(),
                    subnetNode.getArchiveVersion());

            Map<String, AbstractPropertyValue> newProps = new LinkedHashMap<>();
            newProps.put("name", new ScalarPropertyValue(subA4CName));
            newProps.put("region", new ScalarPropertyValue(region));
            newProps.put("ip_cidr_range", new ScalarPropertyValue(cidr));


            subnetNodeTemplate.setProperties(newProps);
            // Creating a new dependency relationship between the Network and its sub-network
            relationshipsToAdd.add(new RelationshipCreation(
                    "tosca.relationships.DependsOn",
                    subnetNodeTemplate, // source
                    networkNodeName, // target
                    "dependency", "feature"));
        } else {
            // Only one default subnetwork can be created for a specific cidr in a defined region
            String defaultRegion = ((ScalarPropertyValue) optional.get().getProperties().get("region")).getValue();
            if (region != defaultRegion) {
                return false;
            }
        }

        // Creating a new network relationship between the sub-network and the node
        relationshipsToAdd.add(new RelationshipCreation(
                "yorc.relationships.google.Network",
                node, // source
                subA4CName, // target
                "network", "connection"));

        return true;
    }

    private Optional<NodeTemplate> checkSubnetExists(final Topology topology, final String subnetNodeName) {
        Set<NodeTemplate> subnets = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.Subnetwork", false);
        return subnets.stream().filter(subnet -> subnet.getName().equals(subnetNodeName)).findAny();
    }

    private String buildSubnetNodeName(final String networkName, final String subnetName) {
        return networkName + "_" + subnetName.replace("-", "_");
    }

}
