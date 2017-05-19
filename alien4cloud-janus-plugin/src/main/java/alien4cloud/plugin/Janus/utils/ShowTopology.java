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
import alien4cloud.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.ToscaTypeSearchService;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeGroup;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Requirement;
import org.alien4cloud.tosca.model.templates.Topology;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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


    /**
     * Copy artifacts for this component
     * @param node
     */
    private void copyArtifacts(PaaSNodeTemplate node) {
        String name = node.getId();

        // Check if this component has artifacts
        Map<String, DeploymentArtifact> map = node.getTemplate().getArtifacts();
        if (map == null) {
            log.debug("Component with no artifact: " + name);
            return;
        }

        // Process each artifact
        for (Map.Entry<String, DeploymentArtifact> da : map.entrySet()) {
            String aname =  name + "/" + da.getKey();
            DeploymentArtifact artifact = da.getValue();
            String artRepo = artifact.getArtifactRepository();
            if (artRepo == null) {
                continue;
            }
            printArtifact(artifact);
            log.debug("Processing artifact: " + aname + " located in " + artRepo);
            if (artRepo.equals(ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY)) {
                // TODO These one are not copied today!
                log.warn("TODO copy artifacts from repository");
            } else if (artRepo.equals(ArtifactRepositoryConstants.ALIEN_TOPOLOGY_REPOSITORY)) {
                // Copy artifact from topology repository to the root of archive.
                String from = artifact.getArtifactPath();
                Path to = node.getCsarPath(); // TODO put at root of archive.
                log.debug("Copy " + from + " -> " + to.toString());
                Path artifactPath = Paths.get(from);
                try {
                    Files.copy(artifactPath, to, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log.error("Could not copy artifact " + aname);
                }
            } else {
                log.warn("Do not know what to do with artifacts from " + artRepo);
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

}
