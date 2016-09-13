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

import java.util.Map;

@Slf4j
public class MappingTosca {

    // TODO : Not working when multiple pre_conf
    // TODO : Support post_conf
    public static void addPreConfigureSteps(Topology topology, PaaSTopology paaSTopology) {

        Workflow installWorkflow = topology.getWorkflows().get("install");


        for (Map.Entry<String, PaaSNodeTemplate> entryNode : paaSTopology.getAllNodes().entrySet()) {
            PaaSNodeTemplate node = entryNode.getValue();
            for (PaaSRelationshipTemplate relation : node.getRelationshipTemplates()) {
                String relationType = relation.getTemplate().getType();
                if(relationType.contains("tosca.relationships")) {
                    continue;
                }
                if(!node.getId().equals(relation.getSource())) {
                    continue;
                }

                Map<String, Interface> interfacesMap = relation.getIndexedToscaElement().getInterfaces();
                for (Map.Entry<String, Interface> entryInterface : interfacesMap.entrySet()) {
                    for (Map.Entry<String, Operation> entryOperation : entryInterface.getValue().getOperations().entrySet()) {
                        if(entryOperation.getValue().getImplementationArtifact() != null) {
                            log.info("[addPreConfigureSteps] NodeId : " + node.getId());
                            log.info("[addPreConfigureSteps] RelationType : " + relationType);
                            log.info("[addPreConfigureSteps] Target : " + relation.getTemplate().getTarget());
                            log.info("[addPreConfigureSteps] Step to add : " + entryOperation.getKey());
                            log.info("[addPreConfigureSteps] RequirementName : " + relation.getTemplate().getRequirementName());

                            addOneStepInWorkflow(installWorkflow, entryOperation.getKey() + "_" + node.getId(),node.getId(), entryOperation.getKey() + "/" + relation.getTemplate().getRequirementName());
                        }
                    }
                }
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

    private static void addOneStepInWorkflow(Workflow workflow, String stepName, String node, String operationName) {
        NodeActivityStep preConfStep = new NodeActivityStep();
        preConfStep.setName(stepName);
        preConfStep.setNodeId(node);
        OperationCallActivity operation = new OperationCallActivity();
        operation.setOperationName(operationName);
        operation.setInterfaceName("tosca.interfaces.node.lifecycle.Configure");
        preConfStep.setActivity(operation);

        workflow.addStep(preConfStep);

        if(operationName.equals("pre_configure_source")) {
            addConfigure_source(workflow.getSteps().get("create_" + node), workflow.getSteps().get(node + "_created"), preConfStep, workflow.getSteps().get("pre_configure_target_" + node));
        } else if(operationName.equals("pre_configure_target")) {
            addConfigure_target(workflow.getSteps().get("create_" + node), workflow.getSteps().get(node + "_created"), workflow.getSteps().get("pre_configure_source_" + node), preConfStep);
        } else if(operationName.equals("post_configure_source")) {
            addConfigure_source(workflow.getSteps().get("configure_" + node), workflow.getSteps().get(node + "_configured"), preConfStep, workflow.getSteps().get("post_configure_target_" + node));
        } else if(operationName.equals("post_configure_target")) {
            addConfigure_target(workflow.getSteps().get("configure_" + node), workflow.getSteps().get(node + "_configured"), workflow.getSteps().get("post_configure_source_" + node), preConfStep);
        }


    }

    private static void addConfigure_source(AbstractStep create, AbstractStep created, AbstractStep preConfSource, AbstractStep preConfTarget) {
        if(preConfTarget != null) {
            create.removeFollowing(preConfTarget.getName());
            WorkflowUtils.linkSteps(create, preConfSource);
            WorkflowUtils.linkSteps(preConfSource, preConfTarget);
        } else {
            create.removeFollowing(created.getName());
            WorkflowUtils.linkSteps(create, preConfSource);
            WorkflowUtils.linkSteps(preConfSource, created);
        }
    }
    private static void addConfigure_target(AbstractStep create, AbstractStep created, AbstractStep preConfSource, AbstractStep preConfTarget) {
        if(preConfSource != null) {
            preConfSource.removeFollowing(created.getName());
            WorkflowUtils.linkSteps(preConfSource, preConfTarget);
        } else {
            create.removeFollowing(created.getName());
            WorkflowUtils.linkSteps(create, preConfTarget);
        }
        WorkflowUtils.linkSteps(preConfTarget, created);
    }


}
