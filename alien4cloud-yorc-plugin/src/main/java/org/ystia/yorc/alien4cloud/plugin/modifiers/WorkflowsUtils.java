package org.ystia.yorc.alien4cloud.plugin.modifiers;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.context.ToscaContext;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.types.AbstractInstantiableToscaType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.workflow.NodeWorkflowStep;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.activities.CallOperationWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.SetStateWorkflowActivity;
import org.alien4cloud.tosca.normative.constants.NormativeWorkflowNameConstants;

import static alien4cloud.utils.AlienUtils.safe;

/**
 * A {@code WorkflowsUtils} is a ...
 *
 * @author Loic Albertin
 */
public class WorkflowsUtils {

    /**
     * Replaces delegate operation of type "install" or "uninstall" by there corresponding sequence of
     * set-state and call-operation. call-operation activities are added only if they are implemented for
     * the given {@link NodeTemplate}.
     *
     * @param workflow     the {@link Workflow} to modify
     * @param nodeTemplate the {@link NodeTemplate} for which steps should be modified
     */
    public static void replaceDelegateByCallOperations(Workflow workflow, NodeTemplate nodeTemplate) {
        // Get a copy of the step list to avoid concurrent exceptions
        Map<String, WorkflowStep> relatedSteps =
                safe(workflow.getSteps()).entrySet().stream()
                        // Filter on steps related to the node
                        .filter(e -> e.getValue().getTarget().equals(nodeTemplate.getName()) &&
                                // And having delegate activities
                                safe(e.getValue().getActivities()).stream().anyMatch(
                                        a -> a instanceof DelegateWorkflowActivity &&
                                                (a.getRepresentation().equals(NormativeWorkflowNameConstants.INSTALL) ||
                                                        a.getRepresentation().equals(NormativeWorkflowNameConstants.UNINSTALL))))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        relatedSteps.forEach((sn, s) -> replaceDelegateStep(workflow, sn, s, nodeTemplate));
    }

    private static void replaceDelegateStep(Workflow workflow, String stepName, WorkflowStep step, NodeTemplate nodeTemplate) {
        // There is no way to do it with alien processors
        // so let do it manually
        String hostId = nodeTemplate.getName();
        if (step instanceof NodeWorkflowStep) {
            hostId = ((NodeWorkflowStep) step).getHostId();
        }

        if (NormativeWorkflowNameConstants.INSTALL.equals(workflow.getName())) {

            linkSteps(workflow, step.getPrecedingSteps(), step.getOnSuccess(), step.getOnFailure(),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CREATING),
                    addCallOperationStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CREATE),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CREATED),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CONFIGURING),
                    addCallOperationStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CONFIGURE),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.CONFIGURED),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.STARTING),
                    addCallOperationStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.START),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.STARTED)
            );

        } else {

            linkSteps(workflow, step.getPrecedingSteps(), step.getOnSuccess(), step.getOnFailure(),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.STOPPING),
                    addCallOperationStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.STOP),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.STOPPED),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.DELETING),
                    addCallOperationStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.DELETE),
                    addSetStateStep(workflow, hostId, nodeTemplate, ToscaNodeLifecycleConstants.DELETED)
            );

        }
        step.getPrecedingSteps().forEach(s -> {
            WorkflowStep wfs = workflow.getSteps().get(s);
            if (wfs != null) {
                wfs.getOnSuccess().remove(stepName);
            }
        });
        workflow.getSteps().remove(stepName);
    }

    private static String addSetStateStep(Workflow workflow, String hostId, NodeTemplate nodeTemplate, String state) {
        SetStateWorkflowActivity sswa = new SetStateWorkflowActivity();
        sswa.setStateName(state);
        WorkflowStep ws = new NodeWorkflowStep(nodeTemplate.getName(), hostId, sswa);
        String stepName = nodeTemplate.getName() + "-" + state + "-yorc-generated";
        workflow.getSteps().put(stepName, ws);
        return stepName;
    }

    private static String addCallOperationStep(Workflow workflow, String hostId, NodeTemplate nodeTemplate, String operationName) {
        String stepName = null;
        if (isOperationImplemented(nodeTemplate, ToscaNodeLifecycleConstants.STANDARD, operationName)) {
            CallOperationWorkflowActivity cowa = new CallOperationWorkflowActivity();
            cowa.setInterfaceName(ToscaNodeLifecycleConstants.STANDARD_SHORT);
            cowa.setOperationName(operationName);
            WorkflowStep ws = new NodeWorkflowStep(nodeTemplate.getName(), hostId, cowa);
            stepName = nodeTemplate.getName() + "-" + ToscaNodeLifecycleConstants.STANDARD_SHORT + "." + operationName + "-yorc-generated";
            workflow.getSteps().put(stepName, ws);
        }
        return stepName;
    }

    private static void linkSteps(Workflow workflow, Set<String> preceding, Set<String> onSuccess, Set<String> onFailure, String... steps) {
        String initialStep = null;
        String latestStep = null;
        for (String s : steps) {
            if (s != null) {
                if (initialStep == null) {
                    initialStep = s;
                    linkToStep(workflow, preceding, initialStep);
                }
                if (latestStep != null) {
                    linkFromStep(workflow, latestStep, Collections.singleton(s));
                }
                workflow.getSteps().get(s).setOnFailure(onFailure);
                latestStep = s;
            }
        }
        if (latestStep != null) {
            linkFromStep(workflow, latestStep, onSuccess);
        }
    }

    private static void linkToStep(Workflow workflow, Set<String> fromSteps, String toStep) {
        fromSteps.forEach(s -> {
            WorkflowStep wf = workflow.getSteps().get(s);
            if (wf != null) {
                wf.addFollowing(toStep);
            }
        });
    }

    private static void linkFromStep(Workflow workflow, String fromStep, Set<String> toSteps) {
        workflow.getSteps().get(fromStep).addAllFollowings(toSteps);
    }

    private static boolean isOperationImplemented(NodeTemplate nodeTemplate, String interfaceName, String operationName) {
        if (interfacesContainsOperation(nodeTemplate.getInterfaces(), interfaceName, operationName)) {
            return true;
        }
        AbstractInstantiableToscaType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
        return isOperationImplemented(nodeType, interfaceName, operationName);
    }


    private static boolean isOperationImplemented(AbstractInstantiableToscaType nodeType, String interfaceName, String operationName) {
        return interfacesContainsOperation(nodeType.getInterfaces(), interfaceName, operationName);
    }

    private static boolean interfacesContainsOperation(Map<String, Interface> interfaces, String interfaceName, String operationName) {
        return safe(interfaces).containsKey(interfaceName) &&
                safe(safe(interfaces).get(interfaceName).getOperations()).containsKey(operationName) &&
                safe(safe(interfaces).get(interfaceName).getOperations()).get(operationName).getImplementationArtifact() != null;
    }
}
