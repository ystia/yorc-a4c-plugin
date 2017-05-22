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
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.wf.Workflow;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.ToscaTypeSearchService;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeGroup;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Requirement;
import org.eclipse.jgit.util.FileUtils;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is used for debugging only.
 * It provides some utilities to print info about a deployment topology.
 */
@Slf4j
public class ShowTopology {

    private static final String FILE_TYPE = "tosca.artifacts.File";
    private static final String DIRECTORY_ARTIFACT_TYPE = "fastconnect.artifacts.ResourceDirectory";
    @Resource
    protected ToscaTypeSearchService csarRepositorySearchService;
    @Resource
    ArtifactLocalRepository localRepository;
    @Resource
    private CsarFileRepository fileRepository;

    /**
     * Copy artifacts for this component
     * @param node
     * TODO nothing to do here. put this elsewhere
     */
    private void copyArtifacts(PaaSNodeTemplate node) {
        String name = node.getId();
        ArrayList<Path> artis = new ArrayList<>();

        // Check if this component has artifacts
        Map<String, DeploymentArtifact> map = node.getTemplate().getArtifacts();
        if (map == null) {
            return;
        }

        // Create a local directory
        String home = System.getProperty("user.home");
        File f = new File(home, name);
        try {
            FileUtils.mkdir(f, true);
        } catch (IOException e) {
            log.debug("Cannot create a directory " + name, e);
            return;
        }

        // Process each artifact
        for (Map.Entry<String, DeploymentArtifact> da : map.entrySet()) {
            String aname =  name + "/" + da.getKey();
            File f2 = new File(home, aname);
            if (f2.exists()) {
                f2.delete();
            }
            printArtifact(da.getValue());
            String artRepo = da.getValue().getArtifactRepository();
            if (artRepo == null) {
                continue;
            }
            log.debug("Processing artifact: " + aname);
            if (artRepo.equals(ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY)) {
                log.debug("Artifact in alien_repository");
                try {
                    Files.copy(localRepository.resolveFile(da.getValue().getArtifactRef()), Paths.get(home, aname));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                artis.add(Paths.get(home, aname));
            } else {
                log.debug("Artifact in " + artRepo);

                String nodeTypeRelativePath = node.getIndexedToscaElement().getElementId() + "-" + node.getIndexedToscaElement().getArchiveVersion();
                //try {
                //    log.debug("COPY ART ");

                //copyArtifactFromCsar(child.getCsarPath(), e.getValue().getArtifactRef(), nodeTypeRelativePath, home + "/" + child.getId() + "/" + e.getKey(), e.getValue(), child.getIndexedToscaElement());
                //    artifacts.add(Paths.get(home + "/" + child.getId() + "/" + e.getKey()));

                //} catch (IOException e1) {
                //    log.debug(e1.getStackTrace().toString());
                //}
            }
        }
    }

    /**
     * Print info about Artifact
     * @param da
     */
    private void printArtifact(DeploymentArtifact da) {
        log.debug("*** Artifact : " + da.getArtifactName());
        log.debug("DeployPath=" + da.getDeployPath());
        log.debug("Archive=" + da.getArchiveName() + " " + da.getArchiveVersion());
        log.debug("ArtifactType=" + da.getArtifactType());
        log.debug("ArtifactPath=" + da.getArtifactPath());
        log.debug("ArtifactRepository=" + da.getArtifactRepository());
        log.debug("RepositoryName=" + da.getRepositoryName());
        log.debug("ArtifactRef=" + da.getArtifactRef());
    }

    /**
     * Print info about a Node
     * @param node
     */
    private void printNode(PaaSNodeTemplate node) {
        log.debug("******* Compute Node " + node.getId() + " *******");
        NodeTemplate nt = node.getTemplate();

        log.debug("CsarPath = " + node.getCsarPath());
        log.debug("Type = " + nt.getType());

        // Children
        List<PaaSNodeTemplate> children = node.getChildren();
        for (PaaSNodeTemplate child : children) {
            log.info("Child: " + child.getId());
        }
        
        // properties
        for (String prop : nt.getProperties().keySet()) {
            AbstractPropertyValue absval = nt.getProperties().get(prop);
            if (absval instanceof ScalarPropertyValue) {
                ScalarPropertyValue scaval = (ScalarPropertyValue) absval;
                log.debug(">> Property: " + prop + "=" + scaval.getValue());
            }
        }

        // Attributes
        Map<String, IValue> attrs = nt.getAttributes();
        if (attrs != null) {
            for (String attname : attrs.keySet()) {
                IValue att = attrs.get(attname);
                log.debug(">> Attribute: " + attname + "=" + att);
            }
        }

        // capabilities
        Map<String, Capability> capabilities = nt.getCapabilities();
        if (capabilities != null) {
            for (String capname : capabilities.keySet()) {
                Capability cap = capabilities.get(capname);
                log.debug(">> Capability " + capname);
                log.debug("type : " + cap.getType());
                log.debug("properties : " + cap.getProperties());
            }
        }

        // requirements
        Map <String, Requirement> requirements = nt.getRequirements();
        if (requirements != null) {
            for (String reqname : requirements.keySet()) {
                Requirement req = requirements.get(reqname);
                log.debug(">> Requirement: " + reqname);
                log.debug("type : " + req.getType());
                log.debug("properties : " + req.getProperties());
            }
        }

        // relationships
        Map <String, RelationshipTemplate> relations = nt.getRelationships();
        if (relations != null) {
            for (String relname : relations.keySet()) {
                RelationshipTemplate rel = relations.get(relname);
                log.debug(">> Relationship: " + relname);
                log.debug("type : " + rel.getType());
                log.debug("properties : " + rel.getProperties());
            }
        }

        // artifacts
        Map <String, DeploymentArtifact> artifacts = nt.getArtifacts();
        if (artifacts != null) {
            for (DeploymentArtifact art : artifacts.values()) {
                printArtifact(art);
            }
        }

    }

    /**
     * Log topology infos for debugging
     * @param ctx
     */
    public void topologyInLog(PaaSTopologyDeploymentContext ctx) {
        String paasId = ctx.getDeploymentPaaSId();
        PaaSTopology ptopo = ctx.getPaaSTopology();
        DeploymentTopology dtopo = ctx.getDeploymentTopology();

        // Deployment Workflows
        Map<String, Workflow> workflows = dtopo.getWorkflows();
        for (String wfname : workflows.keySet()) {
            log.debug("***** Workflow " + wfname);
            Workflow wf = workflows.get(wfname);
            log.debug("name: " + wf.getName());
            log.debug("host: " + wf.getHosts().toString());
            log.debug("steps: " + wf.getSteps().keySet().toString());
        }
        
        // Deployment Groups
        Map<String, NodeGroup> groups = dtopo.getGroups();
        if (groups != null) {
            for (String grname : groups.keySet()) {
                NodeGroup group = groups.get(grname);
                log.debug("***** Group " + grname);
                log.debug("name: " + group.getName());
                log.debug("members: " + group.getMembers().toString());
            }
        }

        // PaaS Compute Nodes
        for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
            printNode(node);
        }
    }

    public void copyAllArtifacts(PaaSTopologyDeploymentContext ctx) {
        PaaSTopology ptopo = ctx.getPaaSTopology();
        for (PaaSNodeTemplate node : ptopo.getAllNodes().values()) {
            copyArtifacts(node);
        }
    }

    /* OLD CODE
            // Children
            List<PaaSNodeTemplate> children = node.getChildren();
            for (PaaSNodeTemplate child : children) {
                log.info("Child: " + child.getId());
                NodeTemplate ch = child.getTemplate();
                Map<String, AbstractPropertyValue> properties = ch.getProperties();
                Map<String, DeploymentArtifact> artifs = ch.getArtifacts();
                Map<String, Interface> inter = child.getInterfaces();
                for (Interface interF : inter.values()) {
                    for (Operation opera : interF.getOperations().values()) {
                        if (opera.getImplementationArtifact() != null && !opera.getImplementationArtifact().getArtifactRef().isEmpty()) {
                            log.debug(" implementation artifact : " + opera.getImplementationArtifact().getArtifactRef());
                        }
                    }
                }

                Map<String, DeploymentArtifact> mapArt = child.getTemplate().getArtifacts();
                for (Map.Entry<String, DeploymentArtifact> e : mapArt.entrySet()) {
                    log.debug("artifact: " + e.getKey() + " " + e.getValue());
                    log.debug("path = " + " " + e.getValue().getArtifactRepository()
                            + " " + e.getValue().getArchiveName() + " " + e.getValue().getArtifactRef());
                }

                for(DeploymentArtifact art : artifs.values()){
                    log.debug("REF artifacts : " + art.getArtifactRef());
                    log.debug("NAME artifacts : " + art.getArchiveName());
                    log.debug("REPO artifacts : " + art.getArtifactRepository());
                    log.debug("TYPE : " + art.getArtifactType());
                    log.debug("VERSION : " + art.getArchiveVersion());
                }

                log.debug("Size artifacts : " + child.getTemplate().getArtifacts().size());
                log.debug("Artifacts : " + child.getTemplate().getArtifacts().get(0));
                for (AbstractPropertyValue propertieC : properties.values()) {
                    if (propertieC != null) {
                        log.debug(((ScalarPropertyValue) propertieC).getValue());
                    }
                }
                for (String interf : child.getInterfaces().keySet()) {
                    Interface it =  child.getInterfaces().get(interf);
                    log.debug("CHILD interface: " + interf + " ope=" + it.getOperations() + " desc=" + it.getDescription());
                }
                Map<String, Interface> interfacesC = child.getTemplate().getInterfaces();
                Map<String, Requirement> reqs = child.getTemplate().getRequirements();
                if(interfacesC != null && interfacesC.isEmpty()){
                    log.debug("Interfaces ... c'est vide");
                } else {
                    if(interfacesC != null && !interfacesC.isEmpty()) {
                        log.debug(">>> CHILD interface not null");
                        log.debug("size interface : " + interfacesC.size());
                        log.debug(" Size values : " + interfacesC.values().size());
                        for (Interface interfaceC : interfacesC.values()) {
                            log.debug(">>> CHILD interface 2");
                            log.debug(interfaceC.getDescription());
                            if (interfaceC != null && !interfaceC.getOperations().isEmpty()) {
                                log.debug(">>> CHILD interface if");
                                for (Operation op : interfaceC.getOperations().values()) {
                                    log.debug(">>> CHILD operations");
                                    log.debug("art repo : " + op.getImplementationArtifact().getArtifactRepository());
                                    log.debug("archiv name : " + op.getImplementationArtifact().getArchiveName());
                                    log.debug("OP INPUT : " + op.getInputParameters());
                                    log.debug("outpus : " + op.getOutputs());
                                }
                            }
                        }
                    }
                }
                if(reqs != null && reqs.isEmpty()){
                    log.debug("Requirement .. c'est vide aussi");
                }
                else {
                    for (Requirement requirement : reqs.values()){
                        log.debug("Type requir : " + requirement.getType());
                        log.debug("Type prop : " + requirement.getProperties());
                    }
                }

                log.debug("Child ID : " + child.getId());
                log.debug("Child csarPath : " + child.getCsarPath());
                log.debug("Child getFileName : " + child.getCsarPath().getFileName());
                log.debug("Child getFileSystem : " + child.getCsarPath().getFileSystem());
                log.debug("Child getParent : " + child.getCsarPath().getParent());
                log.debug("Child getRoot : " + child.getCsarPath().getRoot());
                log.debug("Child template : " + child.getTemplate());
                log.debug("Child networknodes : " + child.getNetworkNodes());
                log.debug("Child relationship template : " + child.getRelationshipTemplates());

                if (child.getTemplate().getArtifacts() != null) {
                    log.debug("atifacts size:" + child.getTemplate().getArtifacts().size());
                    log.debug("user home : " + System.getProperty("user.home"));

                } else
                    log.debug("No artifacts");



                // for each relationship we prepare the env variables which will be given to the container
                for (PaaSRelationshipTemplate relation : child.getRelationshipTemplates()) {
                    if (relation.instanceOf(NormativeRelationshipConstants.CONNECTS_TO)) {
                        log.debug("RelationShip deploymentPaasId : " + paasId + " Relation Target : " + relation.getTemplate().getTarget());

                        if (relation.getSource().equals(child.getId())) {
                            log.debug("RelationShip deploymentPaasId : " + paasId + " Relation Target : " + relation.getTemplate().getTarget());
                            log.debug("RelationShip isSourceyes");
                            log.debug("Relation Source : " + relation.getSource());
                        }
                        if (relation.getTemplate().getTarget().equals(child.getId())) {
                            log.debug("Relationship isTarget");
                        }
                    }


                    log.debug("Relation Template : " + relation.getTemplate());
                    this.printRelationTemplate(relation.getTemplate());

                    Map<String, Interface> interfacesMap = relation.getIndexedToscaElement().getInterfaces();
                    for (Map.Entry<String, Interface> entry : interfacesMap.entrySet()) {
                        for (Map.Entry<String, Operation> entry2 : entry.getValue().getOperations().entrySet()) {
                            log.debug(entry2.getKey() + "/" + entry2.getValue().getImplementationArtifact());
                        }
                    }

                }

                ArrayList<Path> artis = new ArrayList<>();
                // if the node has some artifacts, we need to retrieve them
                if (child.getTemplate().getArtifacts() != null) {

                    String home = System.getProperty("user.home");
                    File f = new File(home, child.getId());
                    try {
                        FileUtils.mkdir(f, true);
                    } catch (IOException e) {
                        log.debug(e.getMessage() + e.getStackTrace().toString());
                        e.printStackTrace();
                    }

                    log.debug("artifacts:" + child.getTemplate().getArtifacts().size());

                    Map<String, DeploymentArtifact> map = child.getTemplate().getArtifacts();
                    for (Map.Entry<String, DeploymentArtifact> da : map.entrySet()) {
                        File f2 = new File(home, child.getId() + "/" + da.getKey());
                        if (f2.exists()) {
                            f2.delete();
                        }
                        log.debug("artifact: " + da.getKey() + " " + da.getValue().toString());
                        log.debug("path = " + child.getCsarPath() + " " + da.getValue().getArtifactRepository()
                                + " " + da.getValue().getArchiveName() + " " + da.getValue().getArtifactRef());

                        if (ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(da.getValue().getArtifactRepository())) {
                            log.debug("ART ALIEN ");
                            try {
                                Files.copy(localRepository.resolveFile(da.getValue().getArtifactRef()), Paths.get(home, child.getId() + "/" + da.getKey()));
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            artis.add(Paths.get(home, child.getId() + "/" + da.getKey()));
                        } else {
                            log.debug("ART PAS ALIEN ");

                            String nodeTypeRelativePath = child.getIndexedToscaElement().getElementId() + "-" + child.getIndexedToscaElement().getArchiveVersion();
                            //try {
                            //    log.debug("COPY ART ");

                                //copyArtifactFromCsar(child.getCsarPath(), e.getValue().getArtifactRef(), nodeTypeRelativePath, home + "/" + child.getId() + "/" + e.getKey(), e.getValue(), child.getIndexedToscaElement());
                            //    artifacts.add(Paths.get(home + "/" + child.getId() + "/" + e.getKey()));

                            //} catch (IOException e1) {
                            //    log.debug(e1.getStackTrace().toString());
                            //}
                        }
                    }
                }
            }
        }


    public void printRelationTemplate(RelationshipTemplate relation) {
        log.debug("Relation Template : " + relation.getRequirementName());
        log.debug("  Requirement Type : " + relation.getRequirementType());
        log.debug("  Target : " + relation.getTarget());
        log.debug("  TargetedCapabilityName : " + relation.getTargetedCapabilityName());
        log.debug("  Type : " + relation.getType());
    }
        */
}
