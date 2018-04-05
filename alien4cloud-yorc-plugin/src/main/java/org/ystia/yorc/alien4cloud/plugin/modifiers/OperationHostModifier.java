package org.ystia.yorc.alien4cloud.plugin.modifiers;

import alien4cloud.tosca.context.ToscaContext;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractInheritableToscaType;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.activities.CallOperationWorkflowActivity;
import org.alien4cloud.tosca.normative.constants.NormativeComputeConstants;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;
import static org.alien4cloud.tosca.utils.ToscaTypeUtils.isOfType;

/**
 * A {@code OperationHostModifier} is a ...
 *
 * @author Loic Albertin
 */
@Slf4j
@Component(value = org.ystia.yorc.alien4cloud.plugin.modifiers.OperationHostModifier.YORC_WF_OPERATION_HOST_MODIFIER_TAG)
public class OperationHostModifier extends TopologyModifierSupport {
    public static final String YORC_WF_OPERATION_HOST_MODIFIER_TAG = "yorc-wf-operation-host-modifier";

    @Override
    public void process(Topology topology, FlowExecutionContext context) {


        safe(topology.getNodeTemplates()).forEach((name, nodeTemplate) -> handleNodeTemplate(topology, nodeTemplate));
    }

    private void handleNodeTemplate(Topology topology, NodeTemplate nodeTemplate) {
        if (shouldBeExecutedOnOrchestrator(topology, nodeTemplate)) {
            safe(topology.getWorkflows()).forEach((s, workflow) -> setOperationHostForNode(workflow, nodeTemplate));
        }

    }

    private void setOperationHostForNode(Workflow workflow, NodeTemplate nodeTemplate) {
        safe(workflow.getSteps()).forEach((s, workflowStep) -> {
            if (workflowStep.getTarget().equals(nodeTemplate.getName()) &&
                    safe(workflowStep.getActivities()).stream()
                            .anyMatch(abstractWorkflowActivity -> abstractWorkflowActivity instanceof CallOperationWorkflowActivity)) {
                workflowStep.setOperationHost("ORCHESTRATOR");
            }
        });
    }

    private boolean shouldBeExecutedOnOrchestrator(Topology topology, NodeTemplate nodeTemplate) {
        if (isCustomResource(topology, nodeTemplate)) {
            return true;
        }
        NodeTemplate topLevelHost = getTopLevelHostNode(topology, nodeTemplate);
        boolean isHostedOnService = topLevelHost instanceof ServiceNodeTemplate;
        return !isCompute(topLevelHost) && !isHostedOnService && isCustomResource(topology, topLevelHost);
    }

    private NodeTemplate getTopLevelHostNode(Topology topology, NodeTemplate nodeTemplate) {
        for (RelationshipTemplate relationshipTemplate : nodeTemplate.getRelationships().values()) {
            RelationshipType relationshipType = ToscaContext.get(RelationshipType.class, relationshipTemplate.getType());
            if (isOfType(relationshipType, NormativeRelationshipConstants.HOSTED_ON)) {
                return getTopLevelHostNode(topology, topology.getNodeTemplates().get(relationshipTemplate.getTarget()));
            }
        }
        return nodeTemplate;
    }

    private boolean isCompute(NodeTemplate node) {
        AbstractInheritableToscaType nodeType = ToscaContext.get(AbstractInheritableToscaType.class, node.getType());
        return ToscaTypeUtils.isOfType(nodeType, NormativeComputeConstants.COMPUTE_TYPE);
    }

    private boolean isCustomResource(Topology topology, NodeTemplate node) {
        if (TopologyNavigationUtil.isHosted(topology, node)) {
            return false;
        }
        if (node instanceof ServiceNodeTemplate) {
            return false;
        }
        // TODO check if it's a resource from the location
        return true;
    }
}
