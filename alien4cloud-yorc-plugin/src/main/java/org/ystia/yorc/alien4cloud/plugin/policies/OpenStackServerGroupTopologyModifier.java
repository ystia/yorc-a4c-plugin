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
package org.ystia.yorc.alien4cloud.plugin.policies;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.types.PolicyType;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static alien4cloud.utils.AlienUtils.safe;


@Slf4j
@Component(value = OpenStackServerGroupTopologyModifier.YORC_OPENSTACK_SERVER_GROUP_TOPOLOGY_MODIFIER)
public class OpenStackServerGroupTopologyModifier extends AbstractPolicyTopologyModifier {

    protected static final String YORC_OPENSTACK_SERVER_GROUP_TOPOLOGY_MODIFIER = "yorc-openstack-server-group-modifier";
    private static final String TOSCA_NODES_COMPUTE = "tosca.nodes.Compute";
    private static final String AFFINITY_POLICY = "yorc.openstack.policies.ServerGroupAffinity";
    private static final String ANTI_AFFINITY_POLICY = "yorc.openstack.policies.ServerGroupAntiAffinity";

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    @Override
    @ToscaContextual
    public void process(final Topology topology, final FlowExecutionContext context) {
        log.debug("Processing OpenStack ServerGroupPolicy modifier for topology " + topology.getId());
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            List<PolicyTemplate> policies = safe(topology.getPolicies()).values().stream()
                    .filter(policyTemplate -> Objects.equals(AFFINITY_POLICY, policyTemplate.getType()) || Objects.equals(ANTI_AFFINITY_POLICY, policyTemplate.getType())).collect(Collectors.toList());

            if (!hasDuplicatedTargetsIntoPolicies(policies, context)) {
                safe(policies).forEach(policyTemplate -> apply(policyTemplate, topology, context));
            }
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
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
        PolicyType policyType = toscaTypeSearchService.findMostRecent(PolicyType.class, policy.getType());
        Set<NodeTemplate> validTargets = getValidTargets(policy, topology, policyType.getTargets(),
                invalidName -> context.log().warn("OpenStack ServerGroup policy <{}>: will ignore target <{}> as it IS NOT an instance of <{}>.", policy.getName(),
                        invalidName, TOSCA_NODES_COMPUTE));

        if (!checkIfServerGroupIsRequired(validTargets)) {
            context.log().warn("no valid target found for applying policy <{}>", policy.getName());
            return;
        }

        // Create OpenStack ServerGroup node template
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());

        String serverGroupTypeName = "yorc.nodes.openstack.ServerGroup";
        NodeType serverGroupNodeType = toscaTypeSearchService.findMostRecent(NodeType.class, serverGroupTypeName);
        List<MemberRelationship> relationshipsToAdd = new ArrayList<>();

        // Set policy
        String policyValue = "";
        String strictStr = ((ScalarPropertyValue)policy.getProperties().get("strict")).getValue();
        boolean strict = Boolean.parseBoolean(strictStr);

        switch (policy.getType()) {
            case AFFINITY_POLICY:
                if (strict) {
                    policyValue = "affinity";
                } else {
                    policyValue = "soft-affinity";
                }
                break;
            case ANTI_AFFINITY_POLICY:
                if (strict) {
                    policyValue = "anti-affinity";
                } else {
                    policyValue = "soft-anti-affinity";
                }
                break;
        }
        Map<String, AbstractPropertyValue> properties = new LinkedHashMap<>();
        properties.put("policy", new ScalarPropertyValue(policyValue));

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
        context.getLog().info(String.format("Add server group node template with name:<%s> and policy; <%s>", name, policyValue));

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

    @AllArgsConstructor
    private class MemberRelationship {
        private NodeTemplate sourceNode;
        private String targetNodeName;
        private String requirementName;
        private String targetCapabilityName;
    }
}
