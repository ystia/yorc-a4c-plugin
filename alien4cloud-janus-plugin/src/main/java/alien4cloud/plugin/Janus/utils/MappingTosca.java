package alien4cloud.plugin.Janus.utils;


import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.OperationCallActivity;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.paas.wf.util.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MappingTosca {

    public static void addPreConfigureSteps(Topology topology, PaaSTopology paaSTopology) {

        Workflow installWorkflow = topology.getWorkflows().get("install");
        Workflow uninstallWorkflow = topology.getWorkflows().get("uninstall");

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

                            AbstractStep step = newStep(entryOperation.getKey() + "_" + nodeName + "/" + requirementName, nodeName, entryOperation.getKey() + "/" + requirementName);

                            if (step.getName().contains("pre")) {
                                preConfSteps.add(step);
                            } else if (step.getName().contains("post")) {
                                postConfSteps.add(step);
                            } else if (step.getName().contains("remove")) {
                                deleteSteps.add(step);
                            } else {
                                postStartSteps.add(step);
                            }

                        }
                    }
                }
            }

            // sort in alphabetical order, since source is before target
            Comparator<AbstractStep> alphabeticalComp = (step1, step2) -> step1.getName().compareTo(step2.getName());

            if (!preConfSteps.isEmpty()) {
                Collections.sort(preConfSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, installWorkflow.getSteps().get("create_" + nodeName), installWorkflow.getSteps().get(nodeName + "_created"), preConfSteps);
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
                log.info("delete_" + nodeName);
                linkStepsParallel(uninstallWorkflow, uninstallWorkflow.getSteps().get("delete_" + nodeName), uninstallWorkflow.getSteps().get(nodeName + "_deleted"), deleteSteps);
            }

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
