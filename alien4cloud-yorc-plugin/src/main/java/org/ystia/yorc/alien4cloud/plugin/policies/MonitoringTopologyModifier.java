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
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.PolicyTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static alien4cloud.utils.AlienUtils.safe;

@Slf4j
@Component(value = MonitoringTopologyModifier.YORC_MONITORING_TOPOLOGY_MODIFIER)
public class MonitoringTopologyModifier extends TopologyModifierSupport {
    protected static final String YORC_MONITORING_TOPOLOGY_MODIFIER = "yorc-monitoring-modifier";
    private static final String HTTP_MONITORING_POLICY = "yorc.policies.monitoring.HTTPMonitoring";
    private static final String TCP_MONITORING_POLICY = "yorc.policies.monitoring.TCPMonitoring";



    @Override
    @ToscaContextual
    public void process(final Topology topology, final FlowExecutionContext context) {
        log.debug("Processing OpenStack ServerGroupPolicy modifier for topology " + topology.getId());
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            List<PolicyTemplate> policies = safe(topology.getPolicies()).values().stream()
                    .filter(policyTemplate -> Objects.equals(TCP_MONITORING_POLICY, policyTemplate.getType()) || Objects.equals(HTTP_MONITORING_POLICY, policyTemplate.getType())).collect(Collectors.toList());

            checkDuplicatedTargetsIntoPolicies(policies, context);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void checkDuplicatedTargetsIntoPolicies(final List<PolicyTemplate> policies, final FlowExecutionContext context) {
        Map<String, String> allTargets = new HashMap<>();
        for (PolicyTemplate policy : policies) {
            for (String target :  policy.getTargets()) {
                if (allTargets.containsKey(target)) {
                    context.log().error("Found target <{}> into several policies: <{}, {}>. Only one monitoring policy can be applied to a defined target.", target, allTargets.get(target), policy.getName());
                    return;
                }
                allTargets.put(target, policy.getName());
            }
        }
    }
}
