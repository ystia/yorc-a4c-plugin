package org.ystia.yorc.alien4cloud.plugin.policies;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;


@Slf4j
@Component(value = OpenStackAntiAffinityTopologyModifier.OPENSTACK_ANTI_AFFINITY_TOPOLOGY_MODIFIER)
public class OpenStackAntiAffinityTopologyModifier extends TopologyModifierSupport {

    public static final String OPENSTACK_ANTI_AFFINITY_TOPOLOGY_MODIFIER = "yorc-openstack-anti-affinity-modifier";

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
        log.debug("Nothing to do for the moment !");
    }
}
