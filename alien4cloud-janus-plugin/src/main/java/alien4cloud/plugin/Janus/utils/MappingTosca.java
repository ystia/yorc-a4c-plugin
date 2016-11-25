package alien4cloud.plugin.Janus.utils;

import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.OperationCallActivity;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.paas.wf.util.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.editor.operations.relationshiptemplate.AbstractRelationshipOperation;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.data.util.Pair;

import java.util.*;

@Slf4j
public class MappingTosca {

    public static void addPreConfigureSteps(Topology topology, PaaSTopology paaSTopology) {

        Workflow installWorkflow = topology.getWorkflows().get("install");
        Workflow uninstallWorkflow = topology.getWorkflows().get("uninstall");

        List<Pair<String, AbstractStep>> targetSteps = new ArrayList<>();

        for (Map.Entry<String, PaaSNodeTemplate> entryNode : paaSTopology.getAllNodes().entrySet()) {
            PaaSNodeTemplate node = entryNode.getValue();
            String nodeName = node.getId();
            List<AbstractStep> preConfSteps = new ArrayList<>();
            List<AbstractStep> postConfSteps = new ArrayList<>();
            List<AbstractStep> postStartSteps = new ArrayList<>();
            List<AbstractStep> deleteSteps = new ArrayList<>();

            for (PaaSRelationshipTemplate relation : node.getRelationshipTemplates()) {
                String relationType = relation.getTemplate().getType();
                if (relationType.contains("tosca.relationships")) {
                    continue;
                }
                if (!nodeName.equals(relation.getSource())) {
                    continue;
                }

                Map<String, Interface> interfacesMap = relation.getIndexedToscaElement().getInterfaces();
                for (Map.Entry<String, Interface> entryInterface : interfacesMap.entrySet()) {
                    for (Map.Entry<String, Operation> entryOperation : entryInterface.getValue().getOperations().entrySet()) {
                        if (entryOperation.getValue().getImplementationArtifact() != null) {
                            String requirementName = relation.getTemplate().getRequirementName();
                            log.info("[addPreConfigureSteps] NodeId : " + nodeName);
                            log.info("[addPreConfigureSteps] RelationType : " + relationType);
                            log.info("[addPreConfigureSteps] Target : " + relation.getTemplate().getTarget());
                            log.info("[addPreConfigureSteps] Step to add : " + entryOperation.getKey());
                            log.info("[addPreConfigureSteps] RequirementName : " + requirementName);
                            log.info("");

                            AbstractStep step = newStep(entryOperation.getKey() + "_" + nodeName + "/" + requirementName + "/" + relation.getTemplate().getTarget(), nodeName, entryOperation.getKey() + "/" + requirementName + "/" + relation.getTemplate().getTarget());

                            if (step.getName().contains("pre_configure_target") || step.getName().contains("post_configure_target") ||step.getName().contains("add_source")) {
                                targetSteps.add(Pair.of(relation.getTemplate().getTarget(), step));
                                log.info("TARGET : " + step.getName());
                                log.info("");
                            } else if (step.getName().contains("pre")) {
                                preConfSteps.add(step);
                                log.info("PRE : " + step.getName());
                                log.info("");
                            } else if (step.getName().contains("post")) {
                                postConfSteps.add(step);
                                log.info("POST : " + step.getName());
                                log.info("");
                            } else if (step.getName().contains("remove")) {
                                deleteSteps.add(step);
                                log.info("REMOVE : " + step.getName());
                                log.info("");
                            } else {
                                postStartSteps.add(step);
                                log.info("OTHERS : " + step.getName());
                                log.info("");
                            }

                        }
                    }
                }
            }

            // sort in alphabetical order, since source is before target
            Comparator<AbstractStep> alphabeticalComp = (step1, step2) -> step1.getName().compareTo(step2.getName());

            if (!preConfSteps.isEmpty()) {
                Collections.sort(preConfSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, installWorkflow.getSteps().get(nodeName + "_configuring"), installWorkflow.getSteps().get("configure_" + nodeName), preConfSteps);
            }
            if (!postConfSteps.isEmpty()) {
                Collections.sort(postConfSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, installWorkflow.getSteps().get("configure_" + nodeName), installWorkflow.getSteps().get(nodeName + "_configured"), postConfSteps);
            }
            if (!postStartSteps.isEmpty()) {
                Collections.sort(postStartSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, installWorkflow.getSteps().get("start_" + nodeName), installWorkflow.getSteps().get(nodeName + "_started"), postStartSteps);
            }
            if (!deleteSteps.isEmpty()) {
                linkStepsParallel(uninstallWorkflow, uninstallWorkflow.getSteps().get("delete_" + nodeName), uninstallWorkflow.getSteps().get(nodeName + "_deleted"), deleteSteps);
            }

        }


        for (Pair<String, AbstractStep> pair : targetSteps) {

            String nodeName = pair.getFirst();
            AbstractStep step = pair.getSecond();

            List<AbstractStep> steps = new ArrayList<>();
            steps.add(step);

            String startStep;
            String endStep;

            if(step.getStepAsString().contains("pre_")) {
                startStep = nodeName + "_configuring";
                endStep = "configure_" + nodeName;
            } else if (step.getStepAsString().contains("post_")) {
                startStep = "configure_" + nodeName;
                endStep = nodeName + "_configured";
            } else if (step.getStepAsString().contains("add_")) {
                startStep = "start_" + nodeName;
                endStep = nodeName + "_started";
            } else {
                System.out.println("Error step target : " + step.getStepAsString());
                return;
            }

            linkStepsParallel(installWorkflow, installWorkflow.getSteps().get(startStep), installWorkflow.getSteps().get(endStep), steps);
            System.out.println(nodeName + " " + step.getStepAsString());
        }


        // Parcours with "topology" instead of "PaasTopology", but cannot get the node type definition
//        for (Map.Entry<String, NodeTemplate> entry : topology.getNodeTemplates().entrySet()) {
//            if(entry == null) continue;
//            if(entry.getValue() == null) continue;
//            if(entry.getValue().getRelationships() == null) continue;
//
//            for (Map.Entry<String, RelationshipTemplate> entry2 : entry.getValue().getRelationships().entrySet()) {
//                log.info(entry2.getKey() + " " + entry2.getValue());
//            }
//        }

    }

    private static void linkStepsParallel(Workflow workflow, AbstractStep first, AbstractStep last, List<AbstractStep> middle) {
        first.removeFollowing(last.getName());

        for (AbstractStep step : middle) {
            WorkflowUtils.linkSteps(first, step);
            WorkflowUtils.linkSteps(step, last);

            workflow.addStep(step);
        }

    }

    private static void linkSteps(Workflow workflow, AbstractStep first, AbstractStep last, List<AbstractStep> middle) {
        first.removeFollowing(last.getName());

        Iterator<AbstractStep> it = middle.iterator();
        AbstractStep prev = first;
        AbstractStep step = first;

        while (it.hasNext()) {
            step = it.next();

            WorkflowUtils.linkSteps(prev, step);
            prev = step;

            workflow.addStep(step);
        }

        WorkflowUtils.linkSteps(step, last);
    }

    private static AbstractStep newStep(String stepName, String node, String operationName) {
        NodeActivityStep preConfStep = new NodeActivityStep();
        preConfStep.setName(stepName);
        preConfStep.setNodeId(node);
        OperationCallActivity operation = new OperationCallActivity();
        operation.setOperationName(operationName);
        operation.setInterfaceName("tosca.interfaces.relationship.configure");
        preConfStep.setActivity(operation);

        return preConfStep;
    }

}
