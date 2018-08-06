package org.ystia.yorc.alien4cloud.plugin.modifiers;

import alien4cloud.tosca.context.ToscaContext;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractInheritableToscaType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeComputeConstants;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * A {@code OperationHostModifier} is a Topology modifier that explore a {@link Topology} to detect templates that have operations
 * that should be executed on the orchestrator host. For those operations it adds an operation_host property to "ORCHESTRATOR" in the
 * relevant workflow step.
 *
 * This modifier is automatically added to all locations.
 *
 * @author Loic Albertin
 */
@Slf4j
@Component(value = AbstractMappingModifier.YORC_WF_ABSTRACT_MAPPING_MODIFIER_TAG)
public class AbstractMappingModifier extends TopologyModifierSupport {


    public static final String YORC_WF_ABSTRACT_MAPPING_MODIFIER_TAG = "yorc-wf-abstract-mapping-modifier";

    @Override
    public void process(Topology topology, FlowExecutionContext context) {
        safe((Map<String, NodeTemplate>)context.getExecutionCache().get(FlowExecutionContext.MATCHING_ORIGINAL_NODES)).forEach((name, nodeTemplate) -> handleNodeTemplate(context, topology, nodeTemplate));
    }

    private void handleNodeTemplate(FlowExecutionContext context, Topology topology, NodeTemplate originalNodeTemplate) {
        if(!isCompute(originalNodeTemplate) && ToscaContext.getOrFail(NodeType.class, originalNodeTemplate.getType()).isAbstract()) {
            NodeTemplate nodeTemplate = topology.getNodeTemplates().get(originalNodeTemplate.getName());
            WorkflowsUtils
                    .replaceDelegateByCallOperations(topology.getWorkflows().get(NormativeWorkflowNameConstants.INSTALL), nodeTemplate);
            WorkflowsUtils
                    .replaceDelegateByCallOperations(topology.getWorkflows().get(NormativeWorkflowNameConstants.UNINSTALL), nodeTemplate);
        }
    }

    private boolean isCompute(NodeTemplate node) {
        AbstractInheritableToscaType nodeType = ToscaContext.get(NodeType.class, node.getType());
        return ToscaTypeUtils.isOfType(nodeType, NormativeComputeConstants.COMPUTE_TYPE);
    }
}
