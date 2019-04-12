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

import alien4cloud.tosca.context.ToscaContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;

import java.util.*;
import java.util.function.Consumer;

public abstract class AbstractPolicyTopologyModifier extends TopologyModifierSupport {

    /**
     * Check if identical targets are present in different policies
     * @param policies
     * @param context
     * @return boolean
     */
    protected boolean hasDuplicatedTargetsIntoPolicies(final List<PolicyTemplate> policies, final FlowExecutionContext context) {
        Map<String, String> allTargets = new HashMap<>();
        for (PolicyTemplate policy : policies) {
            for (String target :  policy.getTargets()) {
                if (allTargets.containsKey(target)) {
                    context.log().error("Found target <{}> into several policies: <{}, {}>. Can't associate a target to many policies of type <{}>.", target, allTargets.get(target), policy.getName(), policy.getType());
                    return true;
                }
                allTargets.put(target, policy.getName());
            }
        }
        return false;
    }

    /**
     * Check if defined node templates in policy template targets are valid according to node types defined in policy type
     *
     * @param policyTemplate
     * @param topology
     * @param invalidTargetConsumer
     * @return Set<NodeTemplate>
     */
    protected Set<NodeTemplate> getValidTargets(PolicyTemplate policyTemplate, Topology topology, Set<String> typeTargets,Consumer<String> invalidTargetConsumer) {
        Set<NodeTemplate> targetedMembers = TopologyNavigationUtil.getTargetedMembers(topology, policyTemplate);
        if (typeTargets.isEmpty()) {
            return targetedMembers;
        }

        Iterator<NodeTemplate> iter = targetedMembers.iterator();
        while (iter.hasNext()) {
            NodeTemplate nodeTemplate = iter.next();
            NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
            boolean isValid = false;
            for (String typeTarget : typeTargets) {
                if (Objects.equals(typeTarget, nodeTemplate.getType()) || nodeType.getDerivedFrom().contains(typeTarget)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                invalidTargetConsumer.accept(nodeTemplate.getName());
                iter.remove();
            }
        }
        return targetedMembers;
    }
}
