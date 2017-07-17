/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.RestClient;
import alien4cloud.plugin.Janus.utils.MappingTosca;
import alien4cloud.plugin.Janus.utils.ShowTopology;
import alien4cloud.plugin.Janus.utils.ZipTopology;
import alien4cloud.topology.TopologyUtils;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.exporter.ArchiveExportService;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ScalingPolicy;
import org.alien4cloud.tosca.model.templates.Topology;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.alien4cloud.tosca.model.types.RelationshipType;
import org.elasticsearch.common.collect.Maps;

import javax.inject.Inject;

/**
 * deployment task
 */
@Slf4j
public class DeployTask extends AlienTask {
    // Needed Info
    PaaSTopologyDeploymentContext ctx;
    IPaaSCallback<?> callback;

    private ArchiveExportService archiveExportService = new ArchiveExportService();
    private ZipTopology zipTopology = new ZipTopology();
    private RestClient restClient = RestClient.getInstance();

    private final int JANUS_DEPLOY_TIMEOUT = 1000 * 3600 * 24;  // 24 hours

    public DeployTask(PaaSTopologyDeploymentContext ctx, JanusPaaSProvider prov, IPaaSCallback<?> callback) {
        super(prov);
        this.ctx = ctx;
        this.callback = callback;
    }

    /**
     * Execute the Deployment
     */
    public void run() {
        Throwable error = null;

        // Keep Ids in a Map
        String paasId = ctx.getDeploymentPaaSId();
        String alienId = ctx.getDeploymentId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("Deploying " + paasId + "with id : " + alienId);
        orchestrator.putDeploymentId(paasId, alienId);

        // Init Deployment Info from topology
        DeploymentTopology dtopo = ctx.getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> curinfo = setupInstanceInformations(dtopo);
        JanusRuntimeDeploymentInfo jrdi = new JanusRuntimeDeploymentInfo(ctx, DeploymentStatus.INIT_DEPLOYMENT, curinfo, deploymentUrl);
        orchestrator.putDeploymentInfo(paasId, jrdi);
        orchestrator.doChangeStatus(paasId, DeploymentStatus.INIT_DEPLOYMENT);

        // Show Topoloy for debug
        ShowTopology.topologyInLog(ctx);

        // Change topology to be suitable for janus and tosca
        MappingTosca.addPreConfigureSteps(ctx);
        MappingTosca.generateOpenstackFIP(ctx);
        MappingTosca.quoteProperties(ctx);

        Csar myCsar = new Csar(paasId, dtopo.getArchiveVersion());
        String yaml = archiveExportService.getYaml(myCsar, dtopo);
        //Path expanded = archiveRepositry.getExpandedCSAR(dtopo.getArchiveName(), dtopo.getArchiveVersion());
        //log.debug(expanded.toString());

        // This operation must be synchronized, because it uses the same files topology.yml and topology.zip
        String taskUrl;
        synchronized(this) {
            // Create the yml of our topology (after substitution)
            // We use local files here
            List<String> lines = Collections.singletonList(yaml);
            Path orig = Paths.get("original.yml");
            try {
                Files.write(orig, lines, Charset.forName("UTF-8"));
            } catch (IOException e) {
                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }

            // Build our zip topology
            try {
                File zip = new File("topology.zip");
                zipTopology.buildZip(zip, ctx);
            } catch (IOException e) {
                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }

            // put topology zip to Janus
            log.info("PUT Topology to janus");
            try {
                taskUrl = restClient.putTopologyToJanus(paasId);
            } catch (Exception e) {
                orchestrator.sendMessage(paasId, "Deployment not accepted by janus: " + e.getMessage());
                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        jrdi.setDeployTaskId(taskId);
        orchestrator.sendMessage(paasId, "Deployment sent to Janus. TaskId=" + taskId);

        // Listen Events and logs from janus about the deployment
        orchestrator.addTask(new EventListenerTask(ctx, orchestrator));
        orchestrator.addTask(new LogListenerTask(ctx, orchestrator));

        // wait for janus deployment completion
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_DEPLOY_TIMEOUT;
        Event evt;
        while (!done && error == null) {
            synchronized (jrdi) {
                // Check deployment timeout
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Deployment Timeout occured");
                    error = new Throwable("Deployment timeout");
                    orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                    break;
                }
                // Wait Deployment Events from Janus
                log.debug(paasId + ": Waiting for deployment events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for deployment");
                }
                // Check if we received a Deployment Event and process it
                evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_DEPLOYMENT)) {
                    jrdi.setLastEvent(null);
                    switch (evt.getStatus()) {
                        case "deployment_failed":
                            log.warn("Deployment failed: " + paasId);
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                            error = new Exception("Deployment failed");
                            break;
                        case "deployed":
                            log.debug("Deployment success: " + paasId);
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.DEPLOYED);
                            done = true;
                            break;
                        case "deployment_in_progress":
                            orchestrator.doChangeStatus(paasId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
                            break;
                        default:
                            orchestrator.sendMessage(paasId, "Deployment status = " + evt.getStatus());
                            break;
                    }
                    continue;
                }
            }
            // We were awaken for some bad reason or a timeout
            // Check Deployment Status to decide what to do now.
            String status;
            try {
                status = restClient.getStatusFromJanus(deploymentUrl);
            } catch (Exception e) {
                // TODO Check error 404
                // assumes it is undeployed
                status = "UNDEPLOYED";
            }
            switch (status) {
                case "UNDEPLOYED":
                    orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                    error = new Throwable("Deployment has been undeployed");
                    break;
                case "DEPLOYED":
                    // Deployment is OK.
                    orchestrator.changeStatus(paasId, DeploymentStatus.DEPLOYED);
                    done = true;
                    break;
                default:
                    log.debug("Deployment Status is currently " + status);
                    break;
            }
        }
        synchronized (jrdi) {
            // Task is ended: Must remove the taskId and notify a possible undeploy waiting for it.
            jrdi.setDeployTaskId(null);
            jrdi.notify();
        }
        // Return result to a4c
        if (error == null) {
            callback.onSuccess(null);
        } else {
            callback.onFailure(error);
        }
    }

    private Map<String, Map<String, InstanceInformation>> setupInstanceInformations(Topology topology) {
        log.debug("setupInstanceInformations for " + topology.getArchiveName() + " : " + topology.getArchiveVersion());
        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        if (nodeTemplates == null) {
            nodeTemplates = Maps.newHashMap();
        }
        Map<String, Map<String, InstanceInformation>> currentInformations = Maps.newHashMap();
        for (Entry<String, NodeTemplate> nodeTemplateEntry : nodeTemplates.entrySet()) {
            Map<String, InstanceInformation> instanceInformations = Maps.newHashMap();
            currentInformations.put(nodeTemplateEntry.getKey(), instanceInformations);
            ScalingPolicy policy = getScalingPolicy(nodeTemplateEntry.getKey(), nodeTemplates);
            int initialInstances = policy != null ? policy.getInitialInstances() : 1;
            for (int i = 0; i < initialInstances; i++) {
                InstanceInformation newInstanceInformation = orchestrator.newInstance(i);
                instanceInformations.put(String.valueOf(i), newInstanceInformation);
            }
        }
        return currentInformations;
    }

    private ScalingPolicy getScalingPolicy(String id, Map<String, NodeTemplate> nodeTemplates) {
        // Get the scaling of parent if not exist
        Capability scalableCapability = TopologyUtils.getScalableCapability(nodeTemplates, id, false);
        if (scalableCapability != null) {
            return TopologyUtils.getScalingPolicy(scalableCapability);
        }
        if (nodeTemplates.get(id).getRelationships() != null) {
            for (RelationshipTemplate rel : nodeTemplates.get(id).getRelationships().values()) {
                RelationshipType relType = orchestrator.getRelationshipType(rel.getType());
                if (ToscaUtils.isFromType(NormativeRelationshipConstants.HOSTED_ON, relType)) {
                    return getScalingPolicy(rel.getTarget(), nodeTemplates);
                }
            }
        }
        return null;
    }

}
