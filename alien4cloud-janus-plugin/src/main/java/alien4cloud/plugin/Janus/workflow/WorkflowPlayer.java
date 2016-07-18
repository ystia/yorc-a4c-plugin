/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.workflow;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.plugin.Janus.utils.ExecPython;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by xBD on 03/06/2016.
 */
@Slf4j
@Getter
@Setter
public class WorkflowPlayer {

    private ExecPython execPython = new ExecPython();
    private List<String> NodeId;
    private static final String NONE = "none";

    /**
     * Scroll down the list of steps in order to deploy our application
     * @param deploymentContext
     * @param workflowSteps
     */
    public void play(PaaSTopologyDeploymentContext deploymentContext, List<WorkflowStep> workflowSteps) throws InterruptedException {
        Topology topology = deploymentContext.getDeploymentTopology();
        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();
        Map<String, Workflow> workflows = topology.getWorkflows();
        String hosts = workflows.get(Workflow.INSTALL_WF).getHosts().toString();

        for (WorkflowStep workflowStep : workflowSteps) {
            //STEP : CREATE
            //
            if (workflowStep.getWorkflowStep().equals(WorkflowReader.CREATE)) {
                log.info("---------");
                log.info("Create : " + workflowStep.getWorkflowId());
                PaaSNodeTemplate child = getChild(paaSTopology, workflowStep.getWorkflowId());
                List<String> properties = getProperties(child);
                String parentPath = child.getCsarPath().getParent().toString();
                String artifactPath = getArtifactRef(child,WorkflowReader.CREATE);
                String scriptPath = getScriptPath(parentPath, artifactPath);
                String gpu = ((ScalarPropertyValue)child.getParent().getTemplate().getProperties().get("gpuType")).getValue();
                log.info("Property GPU :" + gpu);

                //We looking for a Gpu propertie in the host if any
                if(gpu.equals(NONE)){
                    log.info("url image docker : " + properties.get(0));
                   // execPython.componentInstall(getNodeId(), properties.get(0), scriptPath, gpu);
                }else{
                    log.info("url image docker : " + properties.get(1));
                   // execPython.componentInstall(getNodeId(), properties.get(1), scriptPath, gpu);
                }
            //STEP : CONFIGURE
            //for this poc we use the HPCDockerContainer component which have only a create step
            } else if (workflowStep.getWorkflowStep().equals(WorkflowReader.CONFIGURE)) {
                log.info("Configure : " + workflowStep.getWorkflowId());
                log.info("**Do nothing**");

            //STEP : START
            //for this poc we use the HPCDockerContainer component which have only a create step
            } else if (workflowStep.getWorkflowStep().equals(WorkflowReader.START)) {
                log.info("Start : " + workflowStep.getWorkflowId());
                log.info("**Do nothing**");

            //HOSTS of those components
            //STEP : INSTALL
            } else if (hosts.contains(workflowStep.getWorkflowId())) {
                log.info("Compute ID : " + workflowStep.getWorkflowId() + " | Compute Step : " + workflowStep.getWorkflowStep());
                PaaSNodeTemplate node = getNode(paaSTopology, workflowStep.getWorkflowId());
                if((node.getTemplate().getProperties().get("gpuType")) != null){
                    if (((ScalarPropertyValue) node.getTemplate().getProperties().get("gpuType")).getValue() != NONE) {
                        setNodeId(execPython.nodeInstall());
                    } else {
                        setNodeId(execPython.nodeInstall());
                    }
                }else{
                    log.info("gpuType missing");
                }

            //STEP : other steps like configuring, creating, ... which are not key steps
            }else{
                log.info(workflowStep.getWorkflowStep() + " : " + workflowStep.getWorkflowId());
                log.info("**Do nothing**");
            }

        }
    }

    /**
     *
     * @param path
     * @param artifactPath
     * @return path of the script in the CSAR folder
     */
    private String getScriptPath(String path, String artifactPath) {
        if (System.getProperty("os.name").contains("Windows")){
            return path + "\\expanded\\" + artifactPath;
        }else{
            return path + "/expanded/" + artifactPath;
        }
    }

    /**
     *
     * @param child
     * @return
     */
    private List<String> getProperties(PaaSNodeTemplate child) {
        Map<String, AbstractPropertyValue> properties = child.getTemplate().getProperties();
        List<String> propertiesChild = new ArrayList<>();
        for (AbstractPropertyValue propertieChild : properties.values()){
            if (propertieChild != null)
                propertiesChild.add(((ScalarPropertyValue) propertieChild).getValue());
        }
        return propertiesChild;
    }

    /**
     *
     * @param paaSTopology
     * @param nodeId
     * @return node if any
     */
    public PaaSNodeTemplate getNode(PaaSTopology paaSTopology, String nodeId){
        for (PaaSNodeTemplate node : paaSTopology.getComputes()) {
            if (node.getId().equals(nodeId)){
                return node;
            }
        }
        return null;
    }

    /**
     *
     * @param paaSTopology
     * @param childId
     * @return child if any
     */
    public PaaSNodeTemplate getChild(PaaSTopology paaSTopology, String childId){
        for (PaaSNodeTemplate node : paaSTopology.getComputes()) {
            List<PaaSNodeTemplate> children = node.getChildren();
            for (PaaSNodeTemplate child : children) {
                if(child.getId().equals(childId)){
                    return child;
                }
            }
        }
        return null;
    }

    /**
     *
     * @param child (Alien component)
     * @param operation (create, configure, create)
     * @return artifactRef (path to the script of the operation)
     */
    public String getArtifactRef(PaaSNodeTemplate child, String operation){
        Map<String, Interface> interfacesChild = child.getInterfaces();
        for (Interface interfaceChild : interfacesChild.values()) {
            if (interfaceChild.getOperations() != null && interfaceChild.getOperations().get(operation).getImplementationArtifact() != null && !interfaceChild.getOperations().get(operation).getImplementationArtifact().getArtifactRef().isEmpty()) {
                return interfaceChild.getOperations().get(operation).getImplementationArtifact().getArtifactRef();
            }
        }
        return null;
    }

}
