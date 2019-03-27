package org.ystia.yorc.alien4cloud.plugin.policies;

import alien4cloud.model.common.Tag;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

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
    private static final String POLICY_TAG = "policy";

    @Override
    @ToscaContextual
    public void process(final Topology topology, final FlowExecutionContext context) {
        log.debug("Processing OpenStack ServerGroupPolicy modifier for topology " + topology.getId());
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);

            List<PolicyTemplate> policies = safe(topology.getPolicies()).values().stream()
                    .filter(policyTemplate -> Objects.equals(SERVER_GROUP_POLICY, policyTemplate.getType())).collect(Collectors.toList());

            safe(policies).forEach(policyTemplate -> apply(policyTemplate, topology, context));
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void apply(final PolicyTemplate policy, final Topology topology, final FlowExecutionContext context) {
        if (policy.getProperties() == null || policy.getProperties().get("policy") == null) {
            context.log().error("policy property for {} must be filled", SERVER_GROUP_POLICY);
            return;
        }

        Set<NodeTemplate> validTargets = getValidTargets(policy, topology,
                invalidName -> context.log().warn("OpenStack ServerGroup policy <{}>: will ignore target <{}> as it IS NOT an instance of <{}>.", policy.getName(),
                        invalidName, TOSCA_NODES_COMPUTE));


        String policyType = ((ScalarPropertyValue) policy.getProperties().get("policy")).getValue();
        validTargets.forEach(target -> safe(target.getCapabilities()).forEach((key, capability) -> {
            if ("tosca.capabilities.Scalable".equals(capability.getType())) {
                String maxInstancesVal = ((ScalarPropertyValue) capability.getProperties().get("max_instances")).getValue();
                int maxInstances = Integer.parseInt(maxInstancesVal);
                if (maxInstances > 1) {
                    context.getLog().info(String.format("Add policy tag <%s> for node name:<%s>", policyType, target.getName()));
                    List<Tag> tags = new ArrayList<>();
                    Tag tag = new Tag();
                    tag.setName(POLICY_TAG);
                    tag.setValue(policyType);
                    tags.add(tag);
                    target.setTags(tags);
                }
            }
        }));
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
}
