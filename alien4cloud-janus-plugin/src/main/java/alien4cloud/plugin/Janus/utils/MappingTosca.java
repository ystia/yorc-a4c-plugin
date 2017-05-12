package alien4cloud.plugin.Janus.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.DelegateWorkflowActivity;
import alien4cloud.paas.wf.NodeActivityStep;
import alien4cloud.paas.wf.OperationCallActivity;
import alien4cloud.paas.wf.SetStateActivity;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.paas.wf.util.WorkflowUtils;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.Interface;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.data.util.Pair;

@Slf4j
public class MappingTosca {

    public static void addPreConfigureSteps(final PaaSTopologyDeploymentContext ctx) {
        PaaSTopology ptopo = ctx.getPaaSTopology();
        DeploymentTopology dtopo = ctx.getDeploymentTopology();

        Workflow installWorkflow = dtopo.getWorkflows().get("install");
        Workflow uninstallWorkflow = dtopo.getWorkflows().get("uninstall");

        List<Pair<String, AbstractStep>> targetSteps = new ArrayList<>();

        for (Map.Entry<String, PaaSNodeTemplate> entryNode : ptopo.getAllNodes().entrySet()) {
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

                            if (step.getName().contains("pre_configure_target") || step.getName().contains("post_configure_target") || step.getName().contains("add_source")) {
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
                linkStepsParallel(installWorkflow, getNodeStepWithNameMatching(installWorkflow, nodeName, "_configuring"),
                        getNodeStepWithNameMatching(installWorkflow, nodeName, "configure_"), preConfSteps);
            }
            if (!postConfSteps.isEmpty()) {
                Collections.sort(postConfSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, getNodeStepWithNameMatching(installWorkflow, nodeName, "configure_"),
                        getNodeStepWithNameMatching(installWorkflow, nodeName, "_configured"), postConfSteps);
            }
            if (!postStartSteps.isEmpty()) {
                Collections.sort(postStartSteps, alphabeticalComp);
                linkStepsParallel(installWorkflow, getNodeStepWithNameMatching(installWorkflow, nodeName, "start_"),
                        getNodeStepWithNameMatching(installWorkflow, nodeName, "_started"), postStartSteps);
            }
            if (!deleteSteps.isEmpty()) {
                linkStepsParallel(uninstallWorkflow, getNodeStepWithNameMatching(uninstallWorkflow, nodeName, "delete_"),
                        getNodeStepWithNameMatching(uninstallWorkflow, nodeName, "_deleted"), deleteSteps);
            }

        }


        for (Pair<String, AbstractStep> pair : targetSteps) {

            String nodeName = pair.getFirst();
            AbstractStep step = pair.getSecond();

            List<AbstractStep> steps = new ArrayList<>();
            steps.add(step);

            String startStepMatch;
            String endStepMatch;

            if (step.getStepAsString().contains("pre_")) {
                startStepMatch = "_configuring";
                endStepMatch = "configure_";
            } else if (step.getStepAsString().contains("post_")) {
                startStepMatch = "configure_";
                endStepMatch = "_configured";
            } else if (step.getStepAsString().contains("add_")) {
                startStepMatch = "start_";
                endStepMatch = "_started";
            } else {
                System.out.println("Error step target : " + step.getStepAsString());
                return;
            }

            linkStepsParallel(installWorkflow, getNodeStepWithNameMatching(installWorkflow, nodeName, startStepMatch),
                    getNodeStepWithNameMatching(installWorkflow, nodeName, endStepMatch), steps);
            System.out.println(nodeName + " " + step.getStepAsString());
        }

    }

    private static AbstractStep getNodeStepWithNameMatching(Workflow workflow, String nodeName, String match) {
        for (Map.Entry<String, AbstractStep> stepEntry : workflow.getSteps().entrySet()) {
            if (stepEntry.getValue() instanceof NodeActivityStep) {
                NodeActivityStep nodeActivityStep = (NodeActivityStep) stepEntry.getValue();
                if (nodeActivityStep.getNodeId().equals(nodeName) && stepEntry.getKey().contains(match)) {
                    return stepEntry.getValue();
                }
            }

        }
        log.warn("Unable to found a step with name matching {} for node named {}.", match, nodeName);
        return null;
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

    public static void quoteProperties(final PaaSTopologyDeploymentContext ctx) {
        PaaSTopology ptopo = ctx.getPaaSTopology();
        DeploymentTopology dtopo = ctx.getDeploymentTopology();
        for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
            NodeTemplate nt = node.getTemplate();
            log.debug("Node: " + nt.getName());
            for (String prop : nt.getProperties().keySet()) {
                AbstractPropertyValue absval = nt.getProperties().get(prop);
                if (absval instanceof ScalarPropertyValue) {
                    ScalarPropertyValue scaval = (ScalarPropertyValue) absval;
                    log.debug("  Property: " + prop + "=" + scaval.getValue());
                }
            }
            for (String attr : nt.getAttributes().keySet()) {
                log.debug("  Attribute: " + attr + "=" + nt.getAttributes().get(attr));
            }
        }

    }

    public static void generateOpenstackFIP(final PaaSTopologyDeploymentContext ctx) {
        PaaSTopology ptopo = ctx.getPaaSTopology();
        DeploymentTopology dtopo = ctx.getDeploymentTopology();

        Workflow installWorkflow = dtopo.getWorkflows().get("install");
        Workflow uninstallWorkflow = dtopo.getWorkflows().get("uninstall");

        for (PaaSNodeTemplate nodeTemplate : ptopo.getNetworks()) {
            if ("janus.nodes.openstack.PublicNetwork".equalsIgnoreCase(nodeTemplate.getTemplate().getType())) {
                AbstractPropertyValue networkName = nodeTemplate.getTemplate().getProperties().get("floating_network_name");
                for (PaaSRelationshipTemplate relationshipTemplate : nodeTemplate.getRelationshipTemplates()) {
                    Map<String, AbstractPropertyValue> properties = new LinkedHashMap<>();
                    properties.put("floating_network_name", networkName);

                    Map<String, Capability> capabilities = new LinkedHashMap<>();
                    Capability connectionCap = new Capability();
                    connectionCap.setType("janus.capabilities.openstack.FIPConnectivity");
                    capabilities.put("connection", connectionCap);

                    String sourceName = relationshipTemplate.getSource();
                    String fipName = "FIP" + relationshipTemplate.getSource();
                    NodeTemplate fipNodeTemplate = new NodeTemplate("janus.nodes.openstack.FloatingIP",
                            properties, new LinkedHashMap<>(), new LinkedHashMap<>(),
                            new LinkedHashMap<>(), capabilities, new LinkedHashMap<>(), new LinkedHashMap<>());

                    dtopo.getNodeTemplates().put(fipName, fipNodeTemplate);

                    NodeTemplate sourceNode = dtopo.getNodeTemplates().get(sourceName);

                    for (RelationshipTemplate sourceRelTemplate : sourceNode.getRelationships().values()) {
                        if ("network".equalsIgnoreCase(sourceRelTemplate.getRequirementName())) {
                            sourceRelTemplate.setRequirementType("janus.capabilities.openstack.FIPConnectivity");
                            sourceRelTemplate.setTarget(fipName);
                        }
                    }

                    DelegateWorkflowActivity fipInstallActivity = new DelegateWorkflowActivity();
                    fipInstallActivity.setNodeId(fipName);
                    fipInstallActivity.setWorkflowName("install");
                    NodeActivityStep fipInstallStep = new NodeActivityStep();
                    fipInstallStep.setActivity(fipInstallActivity);
                    fipInstallStep.setNodeId(fipName);
                    fipInstallStep.setName(fipName + "_install");
                    AbstractStep sourceInstallStep = getNodeStepWithNameMatching(installWorkflow, sourceName, "_install");
                    fipInstallStep.addFollowing(sourceInstallStep.getName());
                    AbstractStep nodeTemplateInstallStep = getNodeStepWithNameMatching(installWorkflow, nodeTemplate.getId(), "_install");
                    fipInstallStep.addFollowing(nodeTemplateInstallStep.getName());

                    installWorkflow.addStep(fipInstallStep);

                    DelegateWorkflowActivity fipUninstallActivity = new DelegateWorkflowActivity();
                    fipUninstallActivity.setNodeId(fipName);
                    fipUninstallActivity.setWorkflowName("uninstall");
                    NodeActivityStep fipUninstallStep = new NodeActivityStep();
                    fipUninstallStep.setActivity(fipUninstallActivity);
                    fipUninstallStep.setNodeId(fipName);
                    fipUninstallStep.setName(fipName + "_uninstall");
                    AbstractStep sourceUninstallStep = getNodeStepWithNameMatching(uninstallWorkflow, sourceName, "_uninstall");
                    sourceUninstallStep.addFollowing(fipName + "_uninstall");
                    AbstractStep nodeTemplateUninstallStep = getNodeStepWithNameMatching(uninstallWorkflow, nodeTemplate.getId(), "_uninstall");
                    fipUninstallStep.addFollowing(nodeTemplateUninstallStep.getName());

                    uninstallWorkflow.addStep(fipUninstallStep);

                    NodeActivityStep nodeUninstallStep =
                            (NodeActivityStep) getNodeStepWithNameMatching(uninstallWorkflow, nodeTemplate.getId(), "_uninstall");
                    SetStateActivity configuredActivity = new SetStateActivity();
                    configuredActivity.setNodeId(nodeTemplate.getId());
                    configuredActivity.setStateName("configured");
                    nodeUninstallStep.setActivity(configuredActivity);

                    NodeActivityStep nodeInstallStep =
                            (NodeActivityStep) getNodeStepWithNameMatching(installWorkflow, nodeTemplate.getId(), "_install");
                    SetStateActivity startedActivity = new SetStateActivity();
                    startedActivity.setNodeId(nodeTemplate.getId());
                    startedActivity.setStateName("started");
                    nodeInstallStep.setActivity(startedActivity);
                }

                NodeTemplate depNodeTemplate = dtopo.getNodeTemplates().get(nodeTemplate.getId());
                depNodeTemplate.setType("tosca.nodes.Root");
            }
        }
    }
}
