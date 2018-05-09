/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin.modifiers;

import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.editor.operations.workflow.ConnectStepFromOperation;
import org.alien4cloud.tosca.editor.operations.workflow.RemoveEdgeOperation;
import org.alien4cloud.tosca.editor.processors.workflow.ConnectStepFromProcessor;
import org.alien4cloud.tosca.editor.processors.workflow.RemoveEdgeProcessor;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.springframework.stereotype.Component;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * A {@code OpenStackBSComputeWFModifier} is a Topology modifier that explore a {@link Topology} to swap Compute and BlockStorage
 * workflow steps. This is due to the fact that in Alien the relationship between them is from BlockStorage to Compute while in
 * Yorc (and recent versions of TOSCA) the relationship is from Compute to BlockStorage.
 *
 * @author Loic Albertin
 */
@Slf4j
@Component(value = org.ystia.yorc.alien4cloud.plugin.modifiers.OpenStackBSComputeWFModifier.YORC_OPENSTACK_BS_WF_MODIFIER_TAG)
public class OpenStackBSComputeWFModifier extends TopologyModifierSupport {

    public static final String YORC_OPENSTACK_BS_WF_MODIFIER_TAG = "yorc-openstack-blockstorage-workflow-modifier";
    private static final String YORC_OPENSTACK_BS_TYPE = "tosca.nodes.BlockStorage";
    @Resource
    private RemoveEdgeProcessor removeEdgeProcessor;
    @Resource
    private ConnectStepFromProcessor connectStepFromProcessor;

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
        Workflow installWF = topology.getWorkflows().get("install");
        Workflow uninstallWF = topology.getWorkflows().get("uninstall");

        Set<NodeTemplate> bsSet = TopologyNavigationUtil.getNodesOfType(topology, YORC_OPENSTACK_BS_TYPE, true);

        // Let's process all BS
        bsSet.forEach(bs -> safe(bs.getRelationships()).forEach((rn, rt) -> {
            if ("tosca.capabilities.Attachment".equals(rt.getRequirementType())) {
                // Attachment found
                context.getLog()
                        .info("Found a BlockStorage <{}> with an attachment on <{}>. Let's swap their workflow steps to match Yorc " +
                                        "expectations.",
                                bs.getName(), rt.getTarget());
                String computeNodeName = rt.getTarget();
                // Now lets locate corresponding wf steps in install wf
                for (Map.Entry<String, WorkflowStep> workflowStepEntry : installWF.getSteps().entrySet()) {
                    if (workflowStepEntry.getValue().getTarget().equals(bs.getName())) {
                        for (String precedingStepName : workflowStepEntry.getValue().getPrecedingSteps()) {
                            WorkflowStep precedingStep = installWF.getSteps().get(precedingStepName);
                            if (precedingStep.getTarget().equals(computeNodeName)) {
                                // We do not use swap operation here as it may mess up other workflow edges
                                // First remove the edge between steps
                                RemoveEdgeOperation removeEdgeOperation = new RemoveEdgeOperation();
                                removeEdgeOperation.setWorkflowName(installWF.getName());
                                removeEdgeOperation.setFromStepId(precedingStepName);
                                removeEdgeOperation.setToStepId(workflowStepEntry.getKey());
                                log.debug("Swapping {} with target {}", precedingStepName, workflowStepEntry.getKey());
                                removeEdgeProcessor.process(csar, topology, removeEdgeOperation);
                                // Then reconnect them in the right sequence
                                ConnectStepFromOperation connectStepFromOperation = new ConnectStepFromOperation();
                                connectStepFromOperation.setWorkflowName(installWF.getName());
                                connectStepFromOperation.setFromStepIds(new String[]{workflowStepEntry.getKey()});
                                connectStepFromOperation.setToStepId(precedingStepName);
                                connectStepFromProcessor.process(csar, topology, connectStepFromOperation);
                                break;
                            }
                        }
                        break;
                    }
                }

                // Now lets locate corresponding wf steps in uninstall wf
                for (Map.Entry<String, WorkflowStep> workflowStepEntry : uninstallWF.getSteps().entrySet()) {
                    if (workflowStepEntry.getValue().getTarget().equals(bs.getName())) {
                        for (String onSuccessStepName : workflowStepEntry.getValue().getOnSuccess()) {
                            WorkflowStep onSuccessStep = uninstallWF.getSteps().get(onSuccessStepName);
                            if (onSuccessStep.getTarget().equals(computeNodeName)) {
                                // We do not use swap operation here as it may mess up other workflow edges
                                // First remove the edge between steps
                                RemoveEdgeOperation removeEdgeOperation = new RemoveEdgeOperation();
                                removeEdgeOperation.setWorkflowName(uninstallWF.getName());
                                removeEdgeOperation.setFromStepId(workflowStepEntry.getKey());
                                removeEdgeOperation.setToStepId(onSuccessStepName);
                                log.debug("Swapping {} with target {}", onSuccessStepName, workflowStepEntry.getKey());
                                removeEdgeProcessor.process(csar, topology, removeEdgeOperation);
                                // Then reconnect them in the right sequence
                                ConnectStepFromOperation connectStepFromOperation = new ConnectStepFromOperation();
                                connectStepFromOperation.setWorkflowName(uninstallWF.getName());
                                connectStepFromOperation.setFromStepIds(new String[]{onSuccessStepName});
                                connectStepFromOperation.setToStepId(workflowStepEntry.getKey());
                                connectStepFromProcessor.process(csar, topology, connectStepFromOperation);
                                break;
                            }
                        }
                        break;
                    }
                }

                // Start & Stop makes no sense for those kind of nodes in Yorc as those operations are not implemented.
                // Do not change those WFs
            }
        }));
    }
}


