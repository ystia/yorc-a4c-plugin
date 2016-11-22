/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/

//TODO remove this class when the development end
// this class is a test in order to show all in information about the topology and know what kind of information
// we can get thanks to the PaaSTopologyDeploymentContext

package alien4cloud.plugin.Janus.utils;

import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.ToscaTypeSearchService;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
public class ShowTopology {

    private static final String FILE_TYPE = "tosca.artifacts.File";
    private static final String DIRECTORY_ARTIFACT_TYPE = "fastconnect.artifacts.ResourceDirectory";
    private static final Boolean BOOL = false;
    @Resource
    protected ToscaTypeSearchService csarRepositorySearchService;
    @Resource
    ArtifactLocalRepository localRepository;
    @Resource
    private CsarFileRepository fileRepository;

    public void topologyInLog(PaaSTopologyDeploymentContext deploymentContext) {
        Topology topology = deploymentContext.getDeploymentTopology();
        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();

//        log.info("*********** VERSION 1 *****************");
//        log.info("############################################");
//        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
//        Map<String, Workflow> workflows = topology.getWorkflows();
//        log.info("Workflow install : " + workflows.get(Workflow.INSTALL_WF).getSteps().keySet().toString());
//        log.info("Workflow host : " + workflows.get(Workflow.INSTALL_WF).getHosts().toString());
//        log.info("Workflow name : " + workflows.get(Workflow.INSTALL_WF).getName().toString());
//        workflows.get(Workflow.INSTALL_WF).getHosts();
//
//
//        log.info("===== TOPOLOGY DEPLOY ====");
//        log.info("############################################");
//        String delegateID = topology.getDelegateId();
//        Map<String, NodeGroup> groups = topology.getGroups();
//        log.info("DELEGATE ID :" + delegateID);
//        log.info("GROUPS : " + groups);
//        log.info("WORKFLOWS : " + workflows);
//        log.info("NODE TEMPLATE values :" + nodeTemplates.values());
//        log.info("NODE TEMPLATE entrySet :" + nodeTemplates.entrySet());
//        log.info("NODE TEMPLATE keySet :" + nodeTemplates.keySet());
//
//        log.info("===== Paas TOPOLOGY ====");
//        log.info("############################################");
//        log.info("Groups :" + paaSTopology.getGroups().entrySet());
//        log.info("AllNodes :" + paaSTopology.getAllNodes());
//        log.info("Computes :" + paaSTopology.getComputes());
//        log.info("Networks :" + paaSTopology.getNetworks());
//        log.info("Volumes :" + paaSTopology.getVolumes());

        log.info("===== NODES ====");
        log.info("############################################");

        for (PaaSNodeTemplate node : deploymentContext.getPaaSTopology().getComputes()) {

            //log.info("NODE PROPERTIES GPUTYPE : " + ((ScalarPropertyValue) node.getTemplate().getProperties().get("gpuType")).getValue());
            log.info("node id = " + node.getId());
//
//            log.info("node CsarPath = " + node.getCsarPath());
//            log.info("TYPE = " + node.getTemplate().getType());
//            log.info("Nombre de children = " + node.getChildren().size());
//            log.info("### Capability ###");
//            for (Map.Entry<String, Capability> capabilite : node.getTemplate().getCapabilities().entrySet()) {
//                log.info("Capabilitie value : " + capabilite.getValue());
//                log.info("Capabilitie properties : " + capabilite.getValue().getProperties());
//                log.info("Capabilitie type : " + capabilite.getValue().getType());
//                log.info("Capabilitie KEY : " + capabilite.getKey());
//            }
//            log.info("capability : " + node.getTemplate().getCapabilities());
//            log.info("### Requirements ###");
//            for (Map.Entry<String, Requirement> requirement : node.getTemplate().getRequirements().entrySet()) {
//                log.info("Requirement value : " + requirement.getValue());
//                log.info("Requirement properties : " + requirement.getValue().getProperties());
//                log.info("Requirement type : " + requirement.getValue().getType());
//                log.info("Requirement Key : " + requirement.getKey());
//            }
//            log.info("Requirements : " + node.getTemplate().getRequirements());
//            log.info("Properties : " + node.getTemplate().getProperties());
//            log.info("Artifacts : " + node.getTemplate().getArtifacts());
//            log.info("RelationShips : " + node.getTemplate().getRelationships());
//            log.info("Attributes : " + node.getTemplate().getAttributes());
//            log.info("Interface : " + node.getTemplate().getInterfaces());
//            log.info("Name : " + node.getTemplate().getName());
//            if ((node.getScalingPolicy()) != null) {
//                log.info("POLiCY : " + node.getScalingPolicy().getInitialInstances());
//            }
//            log.info("===== Children ====");
//            log.info("############################################");

            List<PaaSNodeTemplate> children = node.getChildren();
            for (PaaSNodeTemplate child : children) {
                Map<String, AbstractPropertyValue> properties = child.getTemplate().getProperties();
                Map<String, DeploymentArtifact> artifs = child.getTemplate().getArtifacts();

                //   log.info(child.getIndexedToscaElement().getArtifacts().get(0).getArtifactRef());
                Map<String, Interface> inter = child.getInterfaces();
                for (Interface interF : inter.values()) {
                    for (Operation opera : interF.getOperations().values()) {
                        if (opera.getImplementationArtifact() != null && !opera.getImplementationArtifact().getArtifactRef().isEmpty()) {
                            log.info(" impl artif : " + opera.getImplementationArtifact().getArtifactRef());
                        }
                    }
                }
//                log.info("=================================");
//                log.info("atifacts:" + child.getTemplate().getArtifacts().size());
//
//                Map<String, DeploymentArtifact> mapArt = child.getTemplate().getArtifacts();
//                for (Map.Entry<String, DeploymentArtifact> e : mapArt.entrySet()) {
//                    log.info("artifact: " + e.getKey() + " " + e.getValue());
//                    log.info("path = " + " " + e.getValue().getArtifactRepository()
//                            + " " + e.getValue().getArchiveName() + " " + e.getValue().getArtifactRef());
//                }
//                log.info("=================================");
//
//                for(DeploymentArtifact art : artifs.values()){
//                    log.info("REF artifacts : " + art.getArtifactRef());
//                    log.info("NAME artifacts : " + art.getArchiveName());
//                    log.info("REPO artifacts : " + art.getArtifactRepository());
//                    log.info("TYPE : " + art.getArtifactType());
//                    log.info("VERSION : " + art.getArchiveVersion());
//                }
//                log.info("######## ARTIFACTS !!!!!!!!! ########");
//
//                log.info("SIze artifacts : " + child.getTemplate().getArtifacts().size());
//                log.info("Artifacts : " + child.getTemplate().getArtifacts().get(0));
                log.info("######## CHILD PROPERTIES ########");
                for (AbstractPropertyValue propertieC : properties.values()) {
                    if (propertieC != null) {
                        log.info(((ScalarPropertyValue) propertieC).getValue());
                    }
                }
//                log.info("######## GET CHILD interfaces ########" + child.getInterfaces().size());
//                if (child.getInterfaces() != null && child.getInterfaces().size()>0) {
//                    log.info("######## GET CHILD interfaces ########" + child.getInterfaces().get(0));
//                    log.info("######## GET CHILD interfaces ########" + child.getInterfaces().get(0).getOperations());
//                    log.info("######## GET CHILD interfaces ########" + child.getInterfaces().get(0).getDescription());
//                }
//                Map<String, Interface> interfacesC = child.getTemplate().getInterfaces();
//                Map<String, Requirement> requirements = child.getTemplate().getRequirements();
//                if(interfacesC != null && interfacesC.isEmpty()){
//                    log.info("Interfaces ... c'est vide");
//                }
//                else{
//                    if(interfacesC != null && !interfacesC.isEmpty()) {
//                        log.info("######## CHILD interface not null ########");
//                        log.info("size interface : " + interfacesC.size());
//                        log.info(" Size values : " + interfacesC.values().size());
//                        for (Interface interfaceC : interfacesC.values()) {
//                            log.info("######## CHILD interface 2 ########");
//                            log.info(interfaceC.getDescription());
//                            if (interfaceC != null && !interfaceC.getOperations().isEmpty()) {
//                                log.info("######## CHILD interface if!! ########");
//                                for (Operation op : interfaceC.getOperations().values()) {
//                                    log.info("######## CHILD operations  ########");
//                                    log.info("art repo : " + op.getImplementationArtifact().getArtifactRepository());
//                                    log.info("archiv name : " + op.getImplementationArtifact().getArchiveName());
//                                    log.info("OP INPUT : " + op.getInputParameters());
//                                    log.info("outpus : " + op.getOutputs());
//                                }
//                            }
//                        }
//                    }
//                }
//                if(requirements != null && requirements.isEmpty()){
//                    log.info("Requirement .. c'est vide aussi");
//                }
//                else{
//                    for(Requirement requirement : requirements.values()){
//                        log.info("Type requir : " + requirement.getType());
//                        log.info("Type prop : " + requirement.getProperties());
//                    }
//                }

//                log.info("################$$$$$##########################################$");
//                log.info("Child ID : " + child.getId());
//                log.info("Child csarPath : " + child.getCsarPath());
//                log.info("Child getFileName : " + child.getCsarPath().getFileName());
//                log.info("Child getFileSystem : " + child.getCsarPath().getFileSystem());
//                log.info("Child getParent : " + child.getCsarPath().getParent());
//                log.info("Child getRoot : " + child.getCsarPath().getRoot());
//                log.info("################$$$$$##########################################$");
//                log.info("Child template : " + child.getTemplate());
//                log.info("Child networknodes : " + child.getNetworkNodes());
//                log.info("Child relationship template : " + child.getRelationshipTemplates());

//                if(child.getTemplate().getArtifacts() != null) {
//                    log.info("atifacts size:" + child.getTemplate().getArtifacts().size());
//                    log.info("user home : " + System.getProperty("user.home"));
//
//                }else
//                    log.info("No artifacts");


                log.info("################ RELATION ##################$");

                // for each relationship we prepare the env variables which will be given to the container
                for (PaaSRelationshipTemplate relation : child.getRelationshipTemplates()) {
                    if (relation.instanceOf(NormativeRelationshipConstants.CONNECTS_TO)) {
                        log.info("RelationShip deploymentPaasId : " + deploymentContext.getDeploymentPaaSId() + " Relation Target : " + relation.getTemplate().getTarget());

                        if (relation.getSource().equals(child.getId())) {
                            log.info("RelationShip deploymentPaasId : " + deploymentContext.getDeploymentPaaSId() + " Relation Target : " + relation.getTemplate().getTarget());
                            log.info("RelationShip isSourceyes");
                            log.info("Relation Source : " + relation.getSource());
                        }
                        if (relation.getTemplate().getTarget().equals(child.getId())) {
                            log.info("Relationship isTarget");
                        }
                    }


                    log.info("Relation Template : " + relation.getTemplate());
                    this.printRelationTemplate(relation.getTemplate());

                    Map<String, Interface> interfacesMap = relation.getIndexedToscaElement().getInterfaces();
                    for (Map.Entry<String, Interface> entry : interfacesMap.entrySet()) {
                        for (Map.Entry<String, Operation> entry2 : entry.getValue().getOperations().entrySet()) {
                            log.info(entry2.getKey() + "/" + entry2.getValue().getImplementationArtifact());
                        }
                    }

                }

//                ArrayList<Path> artifacts = new ArrayList<>();
//                log.info("######## CSAR TEST ########");
//                log.info("############################################");
//                // if the node has some artifacts, we need to retrieve them
//                // TODO remove BOOL param when the artifact notion is clear
//                if (child.getTemplate().getArtifacts() != null && BOOL) {
//
//                    String home = System.getProperty("user.home");
//                    File f = new File(home, child.getId());
//                    try {
//                        FileUtils.mkdir(f, true);
//                    } catch (IOException e) {
//                        log.info(e.getMessage() + e.getStackTrace().toString());
//                        e.printStackTrace();
//                    }
//
//                    log.info("artifacts:" + child.getTemplate().getArtifacts().size());
//
//                    Map<String, DeploymentArtifact> map = child.getTemplate().getArtifacts();
//                    for (Map.Entry<String, DeploymentArtifact> e : map.entrySet()) {
//                        File f2 = new File(home, child.getId() + "/" + e.getKey());
//                        if (f2.exists()) {
//                            f2.delete();
//                        }
//                        log.info("artifact: " + e.getKey() + " " + e.getValue().toString());
//                        log.info("path = " + child.getCsarPath() + " " + e.getValue().getArtifactRepository()
//                                + " " + e.getValue().getArchiveName() + " " + e.getValue().getArtifactRef());
//
//                        if (ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(e.getValue().getArtifactRepository())) {
//                            log.info("ART ALIEN ");
//                            try {
//                                Files.copy(localRepository.resolveFile(e.getValue().getArtifactRef()), Paths.get(home, child.getId() + "/" + e.getKey()));
//                            } catch (IOException e1) {
//                                e1.printStackTrace();
//                            }
//                            artifacts.add(Paths.get(home, child.getId() + "/" + e.getKey()));
//                        } else {
//                            log.info("ART PAS ALIEN ");
//
//                            String nodeTypeRelativePath = child.getIndexedToscaElement().getElementId() + "-" + child.getIndexedToscaElement().getArchiveVersion();
//                            try {
//                                log.info("COPY ART ");
//
//                                copyArtifactFromCsar(child.getCsarPath(), e.getValue().getArtifactRef(), nodeTypeRelativePath, home + "/" + child.getId() + "/" + e.getKey(), e.getValue(), child.getIndexedToscaElement());
//                                artifacts.add(Paths.get(home + "/" + child.getId() + "/" + e.getKey()));
//
//                            } catch (IOException e1) {
//                                log.info(e1.getStackTrace().toString());
//                            }
//                        }
//                    }
//                }
            }
        }
    }


    public void printRelationTemplate(RelationshipTemplate relation) {
        log.info("----Printing relation Template----");
        log.info("Relation Template getRequirementName : " + relation.getRequirementName());
        log.info("Relation Template getRequirementType : " + relation.getRequirementType());
        log.info("Relation Template getTarget : " + relation.getTarget());
        log.info("Relation Template getTargetedCapabilityName : " + relation.getTargetedCapabilityName());
        log.info("Relation Template getType : " + relation.getType());
        log.info("----   END  ----");


    }
}
