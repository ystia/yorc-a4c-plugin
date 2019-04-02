package org.ystia.yorc.alien4cloud.plugin.policies;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static alien4cloud.utils.AlienUtils.safe;


@Slf4j
@Component(value = OpenStackServerGroupTopologyModifier.YORC_OPENSTACK_SERVER_GROUP_TOPOLOGY_MODIFIER)
public class OpenStackServerGroupTopologyModifier extends TopologyModifierSupport {

    protected static final String YORC_OPENSTACK_SERVER_GROUP_TOPOLOGY_MODIFIER = "yorc-openstack-server-group-modifier";
    private static final String TOSCA_NODES_COMPUTE = "tosca.nodes.Compute";
    private static final String SERVER_GROUP_POLICY = "yorc.openstack.policies.ServerGroupPolicy";

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    @Override
    @ToscaContextual
    public void process(final Topology topology, final FlowExecutionContext context) {
        log.debug("Processing OpenStack ServerGroupPolicy modifier for topology " + topology.getId());
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            List<PolicyTemplate> policies = safe(topology.getPolicies()).values().stream()
                    .filter(policyTemplate -> Objects.equals(SERVER_GROUP_POLICY, policyTemplate.getType())).collect(Collectors.toList());

            if (!checkDuplicatedTargetsIntoPolicies(policies, context)) {
                safe(policies).forEach(policyTemplate -> apply(policyTemplate, topology, context));
            }
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }



    private boolean checkDuplicatedTargetsIntoPolicies(final List<PolicyTemplate> policies, final FlowExecutionContext context) {
        Map<String, String> allTargets = new HashMap<>();
        for (PolicyTemplate policy : policies) {
            for (String target :  policy.getTargets()) {
                if (allTargets.containsKey(target)) {
                    context.log().error("Found target <{}> into several policies: <{}, {}>. Can't associate a target to several policies.", target, allTargets.get(target), policy.getName());
                    return true;
                }
                allTargets.put(target, policy.getName());
            }
        }
        return false;
    }

    // Server group is required only if:
    // - 1 scalable target
    // - at least 2 targets
    private boolean checkIfServerGroupIsRequired(final Set<NodeTemplate> validTargets) {
        if (validTargets.isEmpty()) {
            return false;
        } else if (validTargets.size() > 1) {
            return true;
        }

        for (Map.Entry<String, Capability> entry : validTargets.iterator().next().getCapabilities().entrySet()){
            if ("tosca.capabilities.Scalable".equals(entry.getValue().getType())) {
                String maxInstancesVal = ((ScalarPropertyValue) entry.getValue().getProperties().get("max_instances")).getValue();
                int maxInstances = Integer.parseInt(maxInstancesVal);
                if (maxInstances > 1) {
                    return true;
                }
            }
        }

        return false;
    }

    private void apply(final PolicyTemplate policy, final Topology topology, final FlowExecutionContext context) {
        if (policy.getProperties() == null || policy.getProperties().get("policy") == null) {
            context.log().error("policy property for {} must be filled", SERVER_GROUP_POLICY);
            return;
        }

        Set<NodeTemplate> validTargets = getValidTargets(policy, topology,
                invalidName -> context.log().warn("OpenStack ServerGroup policy <{}>: will ignore target <{}> as it IS NOT an instance of <{}>.", policy.getName(),
                        invalidName, TOSCA_NODES_COMPUTE));

        if (!checkIfServerGroupIsRequired(validTargets)) {
            context.log().warn("no valid target found for applying policy:", SERVER_GROUP_POLICY);
            return;
        }

        // Create OpenStack ServerGroup node template
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        String serverGroupTypeName = "yorc.nodes.openstack.ServerGroup";
        NodeType serverGroupNodeType = toscaTypeSearchService.findMostRecent(NodeType.class, serverGroupTypeName);
        List<MemberRelationship> relationshipsToAdd = new ArrayList<>();

        Map<String, AbstractPropertyValue> properties = new LinkedHashMap<>();
        properties.put("policy", policy.getProperties().get("policy"));

        // Set unique name to serverGroup
        ScalarPropertyValue spv = new ScalarPropertyValue();
        spv.setValue(String.format("sg-%s-%s", topology.getArchiveName(), policy.getName()));
        properties.put("name", spv);

        Map<String, Capability> capabilities = new LinkedHashMap<>();
        Capability groupCap = new Capability();
        groupCap.setType("yorc.capabilities.Group");
        capabilities.put("group", groupCap);

        // Creating a new Server group associated to the policy
        String name = policy.getName() + "_sg";
        NodeTemplate serverGroupNodeTemplate = addNodeTemplate(
                csar,
                topology,
                name,
                serverGroupNodeType.getElementId(),
                serverGroupNodeType.getArchiveVersion());

        serverGroupNodeTemplate.setProperties(properties);
        serverGroupNodeTemplate.setCapabilities(capabilities);
        String policyType = ((ScalarPropertyValue) policy.getProperties().get("policy")).getValue();
        context.getLog().info(String.format("Add server group node template with name:<%s> and policy; <%s>", name, policyType));

        // Add relationship MemberOf with each target
        validTargets.forEach(target -> {
            // Creating a new relationship btw the target and the server group
            relationshipsToAdd.add(new MemberRelationship(
                    target, // source
                    serverGroupNodeTemplate.getName(), // target
                    "group",
                    "group"));
        });

        // Add related relationships
        relationshipsToAdd.forEach( rel -> addRelationshipTemplate(
                csar,
                topology,
                rel.sourceNode,
                rel.targetNodeName,
                "yorc.relationships.MemberOf",
                rel.requirementName,
                rel.targetCapabilityName));
    }

    private Set<NodeTemplate> getValidTargets(PolicyTemplate policyTemplate, Topology topology, Consumer<String> invalidTargetConsumer) {
        Set<NodeTemplate> targetedMembers = TopologyNavigationUtil.getTargetedMembers(topology, policyTemplate);
        Iterator<NodeTemplate> iter = targetedMembers.iterator();
        while (iter.hasNext()) {
            NodeTemplate nodeTemplate = iter.next();
            NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
            if (!Objects.equals(TOSCA_NODES_COMPUTE, nodeTemplate.getType()) && !nodeType.getDerivedFrom().contains(TOSCA_NODES_COMPUTE)) {
                invalidTargetConsumer.accept(nodeTemplate.getName());
                iter.remove();
            }
        }
        return targetedMembers;
    }

    @AllArgsConstructor
    private class MemberRelationship {
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }
}
