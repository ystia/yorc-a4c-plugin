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
                    subnetNode);
            return;
        }

        Set<NodeTemplate> privateNetworksNodes = TopologyNavigationUtil.getNodesOfType(topology, "yorc.nodes.google.PrivateNetwork", false);
        List<GooglePrivateNetworkTopologyModifier.DependsOnRelationship> relationshipsToAdd = new ArrayList<>();
        privateNetworksNodes.forEach(privNetNodeTemplate -> {
            final AbstractPropertyValue subnetPropVal = privNetNodeTemplate.getProperties().get("custom_subnetworks");
            if (subnetPropVal != null && subnetPropVal instanceof ListPropertyValue){
                ListPropertyValue subnets = (ListPropertyValue) subnetPropVal;
                List<Object> subnetsList = subnets.getValue();
                subnetsList.forEach(subnet -> {
                    Map<String, String> properties = (LinkedHashMap<String, String>) subnet;
                    Map<String, AbstractPropertyValue> newProps = new LinkedHashMap<>();
                    properties.forEach((k, v) -> {
                        newProps.put(k, new ScalarPropertyValue(v));
                    });

                    newProps.put("network",  new ScalarPropertyValue(privNetNodeTemplate.getName()));

                    NodeTemplate subnetNodeTemplate = addNodeTemplate(
                            csar,
                            topology,
                            properties.get("name"),
                            subnetNode.getElementId(),
                            subnetNode.getArchiveVersion());

                    subnetNodeTemplate.setProperties(newProps);

                    // Creating a new dependency relationship between the Network and its sub-network
                    relationshipsToAdd.add(new GooglePrivateNetworkTopologyModifier.DependsOnRelationship(
                            subnetNodeTemplate, // source
                            privNetNodeTemplate.getName(), // target
                            "dependency", "feature"));

                    // Need to create network relationship for subnet if any is specified or deduce it from compute zone
                });


            }

        });

        relationshipsToAdd.forEach( rel -> addRelationshipTemplate(
                csar,
                topology,
                rel.sourceNode,
                rel.targetNodeName,
                "tosca.relationships.DependsOn",
                rel.requirementName, rel.targetCapabilityName));
    }

    @AllArgsConstructor
    private class DependsOnRelationship {
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }
}
