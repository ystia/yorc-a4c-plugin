package alien4cloud.plugin.Janus.utils;


import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.OperationCallActivity;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.paas.wf.util.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class MappingTosca {

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

                            addOneStepInWorkflow(installWorkflow, entryOperation.getKey() + "_" + node.getId(),node.getId(), entryOperation.getKey());
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
        workflow.getSteps().get("create_" + node).removeFollowing(node + "_created");
        WorkflowUtils.linkSteps(workflow.getSteps().get("create_" + node), preConfStep);
        WorkflowUtils.linkSteps(preConfStep, workflow.getSteps().get(node + "_created"));
    }


}
