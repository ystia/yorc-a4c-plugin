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
            // Create related subnets for each private network and dependency relationship btw subnet and its network
            createSubnets(privNetNodeTemplate, csar, topology, subnetNode, relationshipsToAdd, context);

            // For each Node Template requiring a connection to this private network
            // A new connection is created to the required subnet instead
            for (NodeTemplate nodeTemplate : new ArrayList<>(topology.getNodeTemplates().values())) {
                if (nodeTemplate.getRelationships() == null) continue;

                nodeTemplate.getRelationships().forEach((rel, relationshipTemplate) -> {
                    if (relationshipTemplate.getTarget().equals(privNetNodeTemplate.getName())) {
                        // The aim is to retrieve appropriate subnet for this relationship and replace the network by its subnet in this relationship
                        // Ignore auto create mode as subnets are automatically handled by Google
                        String autoCreateModeStr = ((ScalarPropertyValue) privNetNodeTemplate.getProperties().get("auto_create_subnetworks")).getValue();
                        boolean autoCreateMode = Boolean.parseBoolean(autoCreateModeStr);
                        if (autoCreateMode) {
                            return;
                        }

                        String existingNetwork = "";
                        if (privNetNodeTemplate.getProperties().get("network_name") != null) {
                            existingNetwork = ((ScalarPropertyValue) privNetNodeTemplate.getProperties().get("network_name")).getValue();
                        }

                        // check first if subnet property is defined in the Google network relationship
                        if (relationshipTemplate.getType().equals("yorc.relationships.google.Network")
                                && relationshipTemplate.getProperties().get("subnet") != null) {
                            final AbstractPropertyValue subnetVal = relationshipTemplate.getProperties().get("subnet");
                            if (subnetVal instanceof ScalarPropertyValue) {
                                String subnet = ((ScalarPropertyValue) subnetVal).getValue();

                                // Check is existing network is used
                                if (!existingNetwork.isEmpty()) {
                                    return;
                                }

                                // Otherwise check is this subnet has a node type
                                String subnetNodeName = buildSubnetNodeName(privNetNodeTemplate.getName(), subnet);
                                Optional<NodeTemplate> opt = findSubnetNodeByName(topology, subnetNodeName);
                                if (opt.isPresent()) {
                                    replaceNetworkRelationship(nodeTemplate, subnetNodeName, privNetNodeTemplate.getName(), relationshipTemplate.getName(), relationshipsToAdd, relationshipsToRemove, context);
                                    return;
                                } else {
                                    context.log().error("No existing \"network_name\" property or subnet found for network <{}> with node <{}> for \"subnet\" property <{}> defined in network relationship",
                                            privNetNodeTemplate.getName(), nodeTemplate.getName(), subnet);
                                }
                            }
                        }

                        // Extract node Google region from Zone
                        if (nodeTemplate.getProperties().get("zone") != null) {
                            String zone = ((ScalarPropertyValue) nodeTemplate.getProperties().get("zone")).getValue();
                            String region = GoogleAddressTopologyModifier.extractRegionFromZone(zone);

                            // check secondly the optional default subnet which can be provided by cidr/cidr_region
                            String defaultSubnetNodeName = buildSubnetNodeName(privNetNodeTemplate.getName(), "default");
                            Optional<NodeTemplate> opt = findSubnetNodeByName(topology, defaultSubnetNodeName);
                            if (opt.isPresent()) {
                                // Check if the region is matching
                                String defaultRegion = ((ScalarPropertyValue) opt.get().getProperties().get("region")).getValue();
                                if (defaultRegion.equals(region)) {
                                    replaceNetworkRelationship(nodeTemplate, defaultSubnetNodeName, privNetNodeTemplate.getName(), relationshipTemplate.getName(), relationshipsToAdd, relationshipsToRemove, context);
                                    return;
                                }
                            }

                            // check finally among all existing subnets and retrieve the first regional matching with the node zone
                            opt = findFirstSubnetNodeByRegion(topology, region);
                            if (opt.isPresent()) {
                                replaceNetworkRelationship(nodeTemplate, opt.get().getName(), privNetNodeTemplate.getName(), relationshipTemplate.getName(), relationshipsToAdd, relationshipsToRemove, context);
                                return;
                            }
                        }

                        // No subnet has been found and no existing network
                        if (existingNetwork.isEmpty()) {
                            context.log().error("No matching subnet found for network <{}> with node <{}>. Check the corresponding regions of each one.",
                                    privNetNodeTemplate.getName(), nodeTemplate.getName());
                        }
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

    private void replaceNetworkRelationship(final NodeTemplate node, final String subnetNodeName, final String networkName, final String relationshipName, final List<RelationshipCreation> relationshipsToAdd, final List<RelationshipRemoval> relationshipsToRemove, final FlowExecutionContext context) {
        // Creating a new network relationship between the sub-network and the node
        relationshipsToAdd.add(new RelationshipCreation(
                "yorc.relationships.google.Network",
                node, // source
                subnetNodeName, // target
                "network", "connection"));

        // Remove this relationship
        relationshipsToRemove.add(new RelationshipRemoval(relationshipName, node.getName()));

        context.log().info(
                "Replace network relationship of node <{}> with network <{}> by subnet <{}>",
                node.getName(),
                networkName,
                subnetNodeName);
    }

    private void createSubnets(final NodeTemplate networkNode, final Csar csar, final Topology topology, final NodeType subnetNode, final List<RelationshipCreation> relationshipsToAdd, final FlowExecutionContext context)
    {
        final AbstractPropertyValue subnetPropVal = networkNode.getProperties().get("custom_subnetworks");
        if (subnetPropVal instanceof ListPropertyValue) {
            ListPropertyValue subnets = (ListPropertyValue) subnetPropVal;
            List<Object> subnetsList = subnets.getValue();
            for (Object subnet : subnetsList) {
                Map<String, Object> props = (LinkedHashMap<String, Object>) subnet;
                String name = (String) props.get("name");
                String subA4CName = buildSubnetNodeName(networkNode.getName(), name);

                // Add subnet node template
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

                context.log().info(
                        "Created a new subnet node name <{}> depending on network <{}>",
                        subA4CName,
                        networkNode.getName());

                subnetNodeTemplate.setProperties(newProps);
                // Creating a new dependency relationship between the Network and its sub-network
                relationshipsToAdd.add(new RelationshipCreation(
                        "tosca.relationships.DependsOn",
                        subnetNodeTemplate, // source
                        networkNode.getName(), // target
                        "dependency", "feature"));
            }
        }

        // Create default subnet with cidr/cidr_region properties
        String cidr;
        String cidrRegion;
        final AbstractPropertyValue cidrVal = networkNode.getProperties().get("cidr");
        if (cidrVal == null) {
            return;
        }

        cidr = ((ScalarPropertyValue) networkNode.getProperties().get("cidr")).getValue();
        if (!cidr.isEmpty()) {
            final AbstractPropertyValue cidrRegionVal = networkNode.getProperties().get("cidr_region");
            if (cidrRegionVal == null) {
                context.log().error("\"cidr_region property\" is mandatory if \"cidr\" property is filled for node: <{}>.", networkNode.getName());
                return;
            }
            cidrRegion = ((ScalarPropertyValue) networkNode.getProperties().get("cidr_region")).getValue();
            if (cidrRegion.isEmpty()) {
                context.log().error("\"cidr_region property\" is mandatory if \"cidr\" property is filled for node: <{}>.", networkNode.getName());
                return;
            }

            String subA4CName = buildSubnetNodeName(networkNode.getName(), "default");
            NodeTemplate subnetNodeTemplate = addNodeTemplate(
                    csar,
                    topology,
                    subA4CName,
                    subnetNode.getElementId(),
                    subnetNode.getArchiveVersion());

            Map<String, AbstractPropertyValue> newProps = new LinkedHashMap<>();
            newProps.put("name", new ScalarPropertyValue(subA4CName));
            newProps.put("region", new ScalarPropertyValue(cidrRegion));
            newProps.put("ip_cidr_range", new ScalarPropertyValue(cidr));


            subnetNodeTemplate.setProperties(newProps);
            // Creating a new dependency relationship between the Network and its sub-network
            relationshipsToAdd.add(new RelationshipCreation(
                    "tosca.relationships.DependsOn",
                    subnetNodeTemplate, // source
                    networkNode.getName(), // target
                    "dependency", "feature"));

            context.log().info(
                    "Created a new subnet node name <{}> depending on network <{}>",
                    subA4CName,
                    networkNode.getName());
        }
    }

    private Optional<NodeTemplate> findSubnetNodeByName(final Topology topology, final String subnetNodeName) {
        Set<NodeTemplate> subnets = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.Subnetwork", false);
        return subnets.stream().filter(subnet -> subnet.getName().equals(subnetNodeName)).findAny();
    }

    private Optional<NodeTemplate> findFirstSubnetNodeByRegion(final Topology topology, final String region) {
        Set<NodeTemplate> subnets = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.Subnetwork", false);
        return subnets.stream().filter(subnet -> ((ScalarPropertyValue) subnet.getProperties().get("region")).getValue().equals(region)).findFirst();
    }

    private String buildSubnetNodeName(final String networkName, final String subnetName) {
        return networkName + "_" + subnetName.replace("-", "_");
    }

}
