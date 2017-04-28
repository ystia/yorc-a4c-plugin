/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.PaaSWorkflowMonitorEvent;
import alien4cloud.paas.model.PaaSWorkflowStepMonitorEvent;
import alien4cloud.plugin.Janus.rest.JanusRestException;
import alien4cloud.plugin.Janus.rest.Response.DeployInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.NodeInfosResponse;
import alien4cloud.utils.MapUtil;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.paas.model.PaaSDeploymentStatusMonitorEvent;
import alien4cloud.paas.model.PaaSInstancePersistentResourceMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import alien4cloud.paas.model.PaaSMessageMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.plugin.Janus.rest.Response.AttributeResponse;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.Response.EventResponse;
import alien4cloud.plugin.Janus.rest.Response.InstanceInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.Link;
import alien4cloud.plugin.Janus.rest.Response.LogEvent;
import alien4cloud.plugin.Janus.rest.Response.LogResponse;
import alien4cloud.plugin.Janus.rest.RestClient;
import alien4cloud.plugin.Janus.utils.MappingTosca;
import alien4cloud.plugin.Janus.utils.ZipTopology;
import alien4cloud.topology.TopologyUtils;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
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
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.elasticsearch.common.collect.Maps;
import static java.nio.file.StandardCopyOption.*;

/**
 * a4c janus plugin
 * This class is abstract since it extends JanusOrchestrator
 */
@Slf4j
public abstract class JanusPaaSProvider implements IOrchestratorPlugin<ProviderConfig> {

    private final Map<String, JanusRuntimeDeploymentInfo> runtimeDeploymentInfos = Maps.newConcurrentMap();
    private final List<AbstractMonitorEvent> toBeDeliveredEvents = new ArrayList<>();
    private ProviderConfig providerConfiguration;
    private Map<String, String> a4cDeploymentIds = Maps.newHashMap();

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    private ZipTopology zipTopology = new ZipTopology();

    private RestClient restClient = RestClient.getInstance();

    private ArchiveExportService archiveExportService = new ArchiveExportService();

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;

    // Should set to infinite, since it is not possible to know how long will take
    // an operation. This value is mainly used for debugging.
    // private final int JANUS_TIMEOUT = 1000 * 3600 * 24;  // 1 day
    private final int JANUS_TIMEOUT = 1000 * 60 * 10;   // 10 mns

    // ------------------------------------------------------------------------------------------------------
    // IPaaSProvider implementation
    // ------------------------------------------------------------------------------------------------------

    /**
     * This method is called by Alien in order to restore the state of the paaS provider after a restart.
     * The provider must implement this method in order to restore its state
     *
     * @param activeDeployments the currently active deployments that Alien has
     */
    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {
        log.info("Init plugin for " + activeDeployments.size() + " active deployments");

        for (Map.Entry<String, PaaSTopologyDeploymentContext> entry : activeDeployments.entrySet()) {
            String key = entry.getKey();
            PaaSTopologyDeploymentContext ctx = entry.getValue();
            log.info("Active deployment: " + key);
            doUpdateDeploymentInfo(ctx);
        }
    }

    /**
     * Get status of a deployment
     *
     * @param ctx      the deployment context
     * @param callback callback when the status is available
     */
    @Override
    public void getStatus(PaaSDeploymentContext ctx, IPaaSCallback<DeploymentStatus> callback) {
        DeploymentStatus status;
        String paasId = ctx.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            status = DeploymentStatus.UNDEPLOYED;
        } else {
            status = jrdi.getStatus();
        }
        callback.onSuccess(status);
    }

    /**
     * Deploy a topology
     *
     * @param ctx the PaaSTopologyDeploymentContext of the deployment
     * @param callback to call when deployment is done or has failed.
     */
    @Override
    public void deploy(PaaSTopologyDeploymentContext ctx, IPaaSCallback<?> callback) {

        // Keep Ids in a Map
        String paasId = ctx.getDeploymentPaaSId();
        String alienId = ctx.getDeploymentId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("Deploying " + paasId + "with id : " + alienId);
        a4cDeploymentIds.put(paasId, alienId);

        // Init Deployment Info from topology
        DeploymentTopology dtopo = ctx.getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> curinfo = setupInstanceInformations(dtopo);
        JanusRuntimeDeploymentInfo jrdi = new JanusRuntimeDeploymentInfo(ctx, DeploymentStatus.INIT_DEPLOYMENT, curinfo, deploymentUrl);
        runtimeDeploymentInfos.put(paasId, jrdi);
        doChangeStatus(paasId, DeploymentStatus.INIT_DEPLOYMENT);

        // Change topology to be suitable for janus and tosca
        MappingTosca.addPreConfigureSteps(ctx);
        MappingTosca.generateOpenstackFIP(ctx);
        MappingTosca.quoteProperties(ctx);

        // This operation must be synchronized, because it uses the same files topology.yml and topology.zip
        synchronized(this) {
            // Create the yml of our topology (after substitution)
            // We use a local file named "topology.yml"
            Csar myCsar = new Csar(paasId, dtopo.getArchiveVersion());
            String yaml = archiveExportService.getYaml(myCsar, dtopo);
            List<String> lines = Collections.singletonList(yaml);
            Path file = Paths.get("topology.yml");
            Path orig = Paths.get("original.yml");
            try {
                Files.write(file, lines, Charset.forName("UTF-8"));  
                Files.write(orig, lines, Charset.forName("UTF-8"));
            } catch (IOException e) {
                doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }

            // Build our zip topology
            try {
                File zip = new File("topology.zip");
                zipTopology.buildZip(zip, ctx);
            } catch (IOException e) {
                doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }

            // put topology zip to Janus
            log.info("PUT Topology to janus");
            try {
                restClient.putTopologyToJanus(paasId);
            } catch (Exception e) {
                doChangeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
                return;
            }
        }
        sendMessage(paasId, "Deployment sent to Janus");

        // Listen Events and logs from janus about the deployment
        listenDeploymentEvent(ctx);
        listenJanusLog(ctx);

        // wait for janus deployment completion
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_TIMEOUT;
        synchronized (jrdi) {
            while (!done) {
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    break;
                }
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for deployment");
                    break;
                }
                Event evt = jrdi.getLastEvent();
                if (evt == null || !evt.getType().equals("deployment")) {
                    // This event is not for us, or a timeout occured.
                    continue;
                }
                switch (evt.getStatus()) {
                    case "deployment_failed":
                        doChangeStatus(paasId, DeploymentStatus.FAILURE);
                        callback.onFailure(new Exception("Deployment failed"));
                        done = true;
                        break;
                    case "deployed":
                        doChangeStatus(paasId, DeploymentStatus.DEPLOYED);
                        callback.onSuccess(null);
                        done = true;
                        break;
                    case "deploying":
                        doChangeStatus(paasId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
                        break;
                    default:
                        sendMessage(paasId, "Deployment status = " + evt.getStatus());
                        break;
                }
                // Event has been processed: remove it.
                jrdi.setLastEvent(null);
            }
        }
        if (! done) {
            // Janus did not reply in time.
            // This should never occur with last version of janus (eventV2)
            String status = null;
            try {
                status = restClient.getStatusFromJanus(deploymentUrl);
            } catch (Exception e) {
                status = "FAILED";
            }
            if (status.equals("DEPLOYED")) {
                // Deployment OK.
                changeStatus(paasId, DeploymentStatus.DEPLOYED);
                callback.onSuccess(null);
            } else {
                // Deployment failed
                changeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(new Throwable("Deployment failed with status " + status));
            }
        }
    }

    /**
     * Undeploy a given topology.
     * TODO check undeploy while deploying
     * @param ctx the context of the un-deployment
     * @param callback
     */
    @Override
    public void undeploy(PaaSDeploymentContext ctx, IPaaSCallback<?> callback) {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.debug("Undeploying " + paasId);

        try {
            restClient.undeployJanus(deploymentUrl);
        } catch (Exception e) {
            changeStatus(paasId, DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }
        sendMessage(paasId, "Undeployment sent to Janus");

        // wait for janus undeployment completion
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_TIMEOUT;
        synchronized (jrdi) {
            while (!done) {
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    break;
                }
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for undeployment");
                    break;
                }
                Event evt = jrdi.getLastEvent();
                if (evt == null || !evt.getType().equals("deployment")) {
                    // This event is not for us, or a timeout occured.
                    continue;
                }
                switch (evt.getStatus()) {
                    case "undeployment_failed":
                        doChangeStatus(paasId, DeploymentStatus.FAILURE);
                        callback.onFailure(new Exception("Undeployment failed"));
                        done = true;
                        break;
                    case "undeployed":
                        doChangeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                        callback.onSuccess(null);
                        // Stop threads and remove info about this deployment
                        jrdi.getExecutor().shutdownNow();
                        runtimeDeploymentInfos.remove(paasId);
                        done = true;
                        break;
                    case "undeploying":
                        doChangeStatus(paasId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);
                        break;
                    default:
                        sendMessage(paasId, "Undeployment: status=" + evt.getStatus());
                        break;
                }
                // Event has been processed: remove it.
                jrdi.setLastEvent(null);
            }
        }
        if (!done) {
            // Janus did not reply on time.
            // This should never occur with last version of janus (eventV2)
            String status = null;
            try {
                status = restClient.getStatusFromJanus(deploymentUrl);
            } catch (Exception e) {
                // assumes it is undeployed
                status = "UNDEPLOYED";
            }
            if (status.equals("UNDEPLOYED")) {
                // Undeployment OK.
                changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                callback.onSuccess(null);
            } else {
                // Undeployment failed
                changeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(new Throwable("Undeployment failed with status " + status));
            }
        }
    }

    /**
     * Scale up/down a node
     *
     * @param ctx  the deployment context
     * @param node id of the compute node to scale up
     * @param nbi  the number of instances to be added (if positive) or removed (if negative)
     * @param callback
     */
    @Override
    public void scale(PaaSDeploymentContext ctx, String node, int nbi, IPaaSCallback<?> callback) {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.info(paasId + " : scaling " + node + " delta=" + nbi);

        String taskUrl = null;
        try {
            taskUrl = restClient.scaleNodeInJanus(deploymentUrl, node, nbi);
        } catch (Exception e) {
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        sendMessage(paasId, "Scaling sent to Janus. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_TIMEOUT;
        synchronized (jrdi) {
            while (!done) {
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    break;
                }
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for task end");
                    break;
                }
                Event evt = jrdi.getLastEvent();
                if (evt == null || !evt.getType().equals("scaling")) {
                    // This event is not for us, or a timeout occured.
                    continue;
                }
                sendMessage(paasId, "Scaling " + evt.getStatus());
                switch (evt.getStatus()) {
                    case "failed":
                        callback.onFailure(new Exception("Scaling failed"));
                        done = true;
                        break;
                    case "canceled":
                        callback.onFailure(new Exception("Scaling canceled"));
                        done = true;
                        break;
                    case "done":
                        callback.onSuccess(null);
                        done = true;
                        break;
                    default:
                        break;
                }
                // Event has been processed: remove it.
                jrdi.setLastEvent(null);
            }
        }
        if (! done) {
            // Janus did not reply on time.
            // This should never occur with last version of janus (eventV2)
            String status;
            try {
                status = restClient.getStatusFromJanus(taskUrl);
                log.debug("Returned status:" + status);
            } catch (Exception e) {
                status = "FAILED";
            }
            if (status.equals("DONE")) {
                // Task OK.
                sendMessage(paasId, "Scaling Successful");
                log.debug("Scaling OK");
                callback.onSuccess(null);
            } else {
                // Task failed
                sendMessage(paasId, "Scaling Failed");
                log.debug("Scaling failed");
                callback.onFailure(new Throwable("Task failed with status " + status));
            }
        }
    }

    /**
     * Launch a workflow.
     *
     * @param ctx the deployment context
     * @param workflowName      the workflow to launch
     * @param inputs            the workflow params
     * @param callback
     */
    @Override
    public void launchWorkflow(PaaSDeploymentContext ctx, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.info(paasId + " Execute workflow " + workflowName);

        String taskUrl = null;
        try {
            taskUrl = restClient.postWorkflowToJanus(deploymentUrl, workflowName, inputs);
        } catch (Exception e) {
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        sendMessage(paasId, "Workflow " + workflowName + " sent to Janus. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_TIMEOUT;
        synchronized (jrdi) {
            while (!done) {
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    break;
                }
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for task end");
                    break;
                }
                Event evt = jrdi.getLastEvent();
                if (evt == null || !evt.getType().equals("workflow")) {
                    // This event is not for us, or a timeout occured.
                    continue;
                }
                sendMessage(paasId, "Workflow " + workflowName + " " + evt.getStatus());
                switch (evt.getStatus()) {
                    case "failed":
                        //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                        callback.onFailure(new Exception("Workflow " + workflowName + " failed"));
                        done = true;
                        break;
                    case "canceled":
                        //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                        callback.onFailure(new Exception("Workflow " + workflowName + " canceled"));
                        done = true;
                        break;
                    case "done":
                        //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                        callback.onSuccess(null);
                        done = true;
                        break;
                    case "initial":
                        // TODO name of subworkflow ?
                        //workflowStarted(paasId, workflowName, "TODO");
                        break;
                    case "running":
                        // TODO get name of step and stage: need update of janus API
                        //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                        break;
                    default:
                        break;
                }
                // Event has been processed: remove it.
                jrdi.setLastEvent(null);
            }
        }
        if (! done) {
            // Janus did not reply on time.
            // This should never occur with last version of janus (eventV2)
            String status;
            try {
                status = restClient.getStatusFromJanus(taskUrl);
                log.debug("Returned status:" + status);
            } catch (Exception e) {
                status = "FAILED";
            }
            if (status.equals("DONE")) {
                // Task OK.
                sendMessage(paasId, "Worlflow Successful");
                log.debug("Workflow OK");
                callback.onSuccess(null);
            } else {
                // Task failed
                sendMessage(paasId, "Workflow Failed");
                log.debug("Workflow failed");
                callback.onFailure(new Throwable("Task failed with status " + status));
            }
        }
    }

    /**
     * Trigger a custom command on a node
     *
     * @param ctx      the deployment context
     * @param request  An object of type {@link NodeOperationExecRequest} describing the operation's execution request
     * @param callback the callback that will be triggered when the operation's result become available
     * @throws OperationExecutionException
     */
    @Override
    public void executeOperation(PaaSTopologyDeploymentContext ctx, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback) throws OperationExecutionException {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.info(paasId + " Execute custom command " + request.getOperationName());

        String taskUrl = null;
        try {
            taskUrl = restClient.postCustomCommandToJanus(deploymentUrl, request);
        } catch (Exception e) {
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        sendMessage(paasId, "Operation " + request.getOperationName() + " sent to Janus. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_TIMEOUT;
        synchronized (jrdi) {
            while (!done) {
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    break;
                }
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for task end");
                    break;
                }
                Event evt = jrdi.getLastEvent();
                if (evt == null || !evt.getType().equals("custom_command")) {
                    // This event is not for us, or a timeout occured.
                    continue;
                }
                // TODO check taskId in case of several custom command in //
                sendMessage(paasId, "Operation " + evt.getStatus());
                switch (evt.getStatus()) {
                    case "failed":
                    case "canceled":
                        callback.onFailure(new Exception("Custom command " + request.getOperationName() + " failed"));
                        done = true;
                        break;
                    case "done":
                        Map<String, String> customResults = new Hashtable<>(1);
                        customResults.put("result", "Succesfully executed custom " + request.getOperationName() + " on node " + request.getNodeTemplateName());
                        // TODO Get results returned by the custom command ??
                        callback.onSuccess(customResults);
                        done = true;
                        break;
                    default:
                        break;
                }
                // Event has been processed: remove it.
                jrdi.setLastEvent(null);
            }
        }
        if (! done) {
            // Janus did not reply on time.
            // This should never occur with last version of janus (eventV2)
            String status;
            try {
                status = restClient.getStatusFromJanus(taskUrl);
                log.debug("Returned status:" + status);
            } catch (Exception e) {
                status = "FAILED";
            }
            if (status.equals("DONE")) {
                // Task OK.
                sendMessage(paasId, "Custom Command Successful");
                log.debug("Operation OK");
                callback.onSuccess(null);
            } else {
                // Task failed
                sendMessage(paasId, "Custom Command Failed");
                log.debug("Operation failed");
                callback.onFailure(new Throwable("Task failed with status " + status));
            }
        }
    }

    /**
     * Get instance information of a topology from the PaaS
     *
     * @param ctx      the deployment context
     * @param callback callback when the information is available
     */
    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext ctx, IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        String paasId = ctx.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.debug(paasId + " getInstancesInformation");
        if (jrdi != null) {
            callback.onSuccess(jrdi.getInstanceInformations());
        } else {
            log.warn("No information about this deployment: " + paasId);
            log.warn("Assuming that it has been undeployed");
            callback.onSuccess(Maps.newHashMap());
        }
    }

    /**
     * Get all audit events that occurred since the given date.
     * The events must be ordered by date as we could use this method to iterate
     * through events in case of many events.
     *
     * @param date      The start date since which we should retrieve events.
     * @param maxEvents The maximum number of events to return.
     * @param callback
     * @return An array of time ordered audit events with a maximum size of maxEvents.
     */
    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> callback) {
        // TODO parameters date and maxevents should be considered
        synchronized(toBeDeliveredEvents) {
            AbstractMonitorEvent[] events = toBeDeliveredEvents.toArray(new AbstractMonitorEvent[toBeDeliveredEvents.size()]);
            callback.onSuccess(events);
            toBeDeliveredEvents.clear();
        }
    }

    /**
     * Switch the maintenance mode for this deployed topology.
     *
     * @param  ctx the deployment context
     * @param  maintenanceModeOn
     * @throws MaintenanceModeException
     */
    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext ctx, boolean maintenanceModeOn) throws MaintenanceModeException {
        String paasId = ctx.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.debug(paasId + " switchMaintenanceMode");
        if (jrdi == null) {
            log.error(paasId + " switchMaintenanceMode: No Deployment Information");
            throw new MaintenanceModeException("No Deployment Information");
        }

        Topology topology = jrdi.getDeploymentContext().getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> nodes = jrdi.getInstanceInformations();
        if (nodes == null || nodes.isEmpty()) {
            log.error(paasId + " switchMaintenanceMode: No Node found");
            throw new MaintenanceModeException("No Node found");
        }
        for (Entry<String, Map<String, InstanceInformation>> nodeEntry : nodes.entrySet()) {
            String node = nodeEntry.getKey();
            Map<String, InstanceInformation> nodeInstances = nodeEntry.getValue();
            if (nodeInstances != null && !nodeInstances.isEmpty()) {
                NodeTemplate nodeTemplate = topology.getNodeTemplates().get(node);
                NodeType nodeType = toscaTypeSearchService.getRequiredElementInDependencies(NodeType.class, nodeTemplate.getType(),
                        topology.getDependencies());
                if (ToscaUtils.isFromType(NormativeComputeConstants.COMPUTE_TYPE, nodeType)) {
                    for (Entry<String, InstanceInformation> nodeInstanceEntry : nodeInstances.entrySet()) {
                        String instance = nodeInstanceEntry.getKey();
                        InstanceInformation iinfo = nodeInstanceEntry.getValue();
                        if (iinfo != null) {
                            doSwitchInstanceMaintenanceMode(paasId, node, instance, iinfo, maintenanceModeOn);
                        }
                    }
                }
            }
        }

    }

    /**
     * Switch the maintenance mode for a given node instance of this deployed topology.
     *
     * @param ctx the deployment context
     * @param node
     * @param instance
     * @param mode
     * @throws MaintenanceModeException
     */
    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext ctx, String node, String instance, boolean mode) throws MaintenanceModeException {
        String paasId = ctx.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.debug(paasId + " switchInstanceMaintenanceMode");
        if (jrdi == null) {
            log.error(paasId + " switchInstanceMaintenanceMode: No Deployment Information");
            throw new MaintenanceModeException("No Deployment Information");
        }
        final Map<String, Map<String, InstanceInformation>> existingInformations = jrdi.getInstanceInformations();
        if (existingInformations != null && existingInformations.containsKey(node)
                && existingInformations.get(node).containsKey(instance)) {
            InstanceInformation iinfo = existingInformations.get(node).get(instance);
            doSwitchInstanceMaintenanceMode(paasId, node, instance, iinfo, mode);
        }
    }


    // ------------------------------------------------------------------------------------------------------
    // IConfigurablePaaSProvider implementation
    // ------------------------------------------------------------------------------------------------------

    /**
     * Set / apply a configuration for a PaaS provider
     *
     * @param configuration The configuration object as edited by the user.
     * @throws PluginConfigurationException In case the PaaS provider configuration is incorrect.
     */
    @Override
    public void setConfiguration(ProviderConfig configuration) throws PluginConfigurationException {
        log.info("set config for JanusPaaSProvider");
        providerConfiguration = configuration;
        restClient.setProviderConfiguration(providerConfiguration);
    }


    // ------------------------------------------------------------------------------------------------------
    // private methods
    // ------------------------------------------------------------------------------------------------------

    /**
     * Change status of the deployment in JanusRuntimeDeploymentInfo
     * @param paasId
     * @param status
     */
    protected void changeStatus(final String paasId, final DeploymentStatus status) {
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        synchronized (jrdi) {
            doChangeStatus(paasId, status);
        }
    }

    /**
     * Actually change the status of the deployment in JanusRuntimeDeploymentInfo
     * Must be called with lock on jrdi
     * @param paasId
     * @param status
     */
    protected void doChangeStatus(String paasId, DeploymentStatus status) {
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        DeploymentStatus oldDeploymentStatus = jrdi.getStatus();
        log.debug("Deployment [" + paasId + "] moved from status [" + oldDeploymentStatus + "] to [" + status + "]");
        jrdi.setStatus(status);

        PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
        event.setDeploymentStatus(status);
        postEvent(event, paasId);
    }

    /**
     * Notify a4c that a workflow has been started
     * @param paasId
     * @param workflow
     * @param subworkflow
     */
    protected void workflowStarted(String paasId, String workflow, String subworkflow) {
        PaaSWorkflowMonitorEvent event = new PaaSWorkflowMonitorEvent();
        // TODO
        event.setSubworkflow(subworkflow);
        event.setWorkflowId(workflow);
        postEvent(event, paasId);
    }

    /**
     * Notify a4c that a workflow has reached a step
     * @param paasId
     * @param workflow
     * @param node
     * @param step
     * @param stage
     */
    protected void workflowStep(String paasId, String workflow, String node, String step, String stage) {
        PaaSWorkflowStepMonitorEvent event = new PaaSWorkflowStepMonitorEvent();
        // TODO
        event.setWorkflowId(workflow);
        event.setStepId(step);
        event.setStage(stage);
        event.setNodeId(node);
        postEvent(event, paasId);
    }

    /**
     * Update Instance State and notify alien4cloud if needed
     * @param paasId Deployment PaaS Id
     * @param nodeId
     * @param instanceId
     * @param iinfo
     * @param state
     */
    protected void updateInstanceState(String paasId, String nodeId, String instanceId, InstanceInformation iinfo, String state) {
        log.debug("paasId=" + paasId + " : set instance state:  " + instanceId + "=" + state);

        // update InstanceInformation
        InstanceStatus status = getInstanceStatusFromState(state);
        iinfo.setState(state);
        iinfo.setInstanceStatus(status);

        // Notify a4c
        PaaSInstanceStateMonitorEvent event = new PaaSInstanceStateMonitorEvent();
        event.setInstanceId(instanceId);
        event.setInstanceState(state);
        event.setInstanceStatus(status);
        event.setNodeTemplateId(nodeId);
        event.setRuntimeProperties(iinfo.getRuntimeProperties());
        event.setAttributes(iinfo.getAttributes());
        postEvent(event, paasId);
    }

    /**
     * Deliver a PaaSMessageMonitorEvent to alien4cloud
     * @param paasId
     * @param message
     */
    protected void sendMessage(final String paasId, final String message) {
        PaaSMessageMonitorEvent event = new PaaSMessageMonitorEvent();
        event.setMessage(message);
        postEvent(event, paasId);
    }

    /**
     * Update Deployment Info from Janus information
     * Called at init, for each active deployment.
     * @param ctx
     */
    protected void doUpdateDeploymentInfo(PaaSTopologyDeploymentContext ctx) {
        String paasId = ctx.getDeploymentPaaSId();
        a4cDeploymentIds.put(paasId, ctx.getDeploymentId());
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("update deployment info " + paasId);

        // Create the JanusRuntimeDeploymentInfo for this deployment
        Map<String, Map<String, InstanceInformation>> nodemap = Maps.newHashMap();
        JanusRuntimeDeploymentInfo jrdi = new JanusRuntimeDeploymentInfo(ctx, DeploymentStatus.UNKNOWN, nodemap, deploymentUrl);
        runtimeDeploymentInfos.put(paasId, jrdi);

        try {
            updateNodeInfo(ctx);
        } catch (Exception e) {
            log.error(paasId + " : Cannot update DeploymentInfo ", e);
        }
    }

    /**
     * Update nodeInformation in the JanusRuntimeDeploymentInfo
     * This is needed to let a4c know all about the nodes and their instances
     * Information is got from Janus using the REST API
     *
     * @param ctx PaaSDeploymentContext to be updated
     *
     * @throws
     */
    private void updateNodeInfo(PaaSDeploymentContext ctx) throws Exception {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("updateNodeInfo " + paasId);

        // Assumes JanusRuntimeDeploymentInfo already created.
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("No JanusRuntimeDeploymentInfo");
            return;
        }

        // Find the deployment info from Janus
        DeployInfosResponse deployRes = restClient.getDeploymentInfosFromJanus(deploymentUrl);
        jrdi.setStatus(getDeploymentStatusFromString(deployRes.getStatus()));

        Map<String, Map<String, InstanceInformation>> nodemap = jrdi.getInstanceInformations();

        // Look every node we want to update.
        for (Link nodeLink : deployRes.getLinks()) {
            if (nodeLink.getRel().equals("node")) {

                // Find the node info from Janus
                NodeInfosResponse nodeRes = restClient.getNodesInfosFromJanus(nodeLink.getHref());
                String node = nodeRes.getName();

                Map<String, InstanceInformation> instanceMap = nodemap.get(node);
                if (instanceMap == null) {
                    // This node was unknown. Create it.
                    instanceMap = Maps.newHashMap();
                    nodemap.put(node, instanceMap);
                }
                // Find information about all the node instances from Janus
                for (Link instanceLink : nodeRes.getLinks()) {
                    if (instanceLink.getRel().equals("instance")) {

                        // Find the instance info from Janus
                        InstanceInfosResponse instRes = restClient.getInstanceInfosFromJanus(instanceLink.getHref());

                        String inb = instRes.getId();
                        InstanceInformation iinfo = instanceMap.get(inb);
                        if (iinfo == null) {
                            // This instance was unknown. create it.
                            iinfo = newInstance(new Integer(inb));
                            instanceMap.put(inb, iinfo);
                        }
                        for (Link link : instRes.getLinks()) {
                            if (link.getRel().equals("attribute")) {
                                // Get the attribute from Janus
                                AttributeResponse attrRes = restClient.getAttributeFromJanus(link.getHref());
                                iinfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                                log.debug("Attribute: " + attrRes.getName() + "=" + attrRes.getValue());
                            }
                        }
                        // Let a4c know the instance state
                        updateInstanceState(paasId, node, inb, iinfo, instRes.getStatus());
                    }
                }
            }
        }
    }

    /**
     * Ask Janus the values of all attributes for this node/instance
     * @param ctx
     * @param node
     * @param instance
     * @throws Exception
     */
    private void updateInstanceAttributes(PaaSDeploymentContext ctx, InstanceInformation iinfo, String node, String instance) throws Exception {
        String paasId = ctx.getDeploymentPaaSId();
        String url = "/deployments/" + paasId + "/nodes/" + node + "/instances/" + instance;
        InstanceInfosResponse instInfoRes = restClient.getInstanceInfosFromJanus(url);
        for (Link link : instInfoRes.getLinks()) {
            if (link.getRel().equals("attribute")) {
                // Get the attribute from Janus
                AttributeResponse attrRes = restClient.getAttributeFromJanus(link.getHref());
                iinfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                log.debug("Attribute: " + attrRes.getName() + "=" + attrRes.getValue());
            }
        }
    }

    /**
     * Switch Instance Maintenance Mode for this instance
     * TODO This is inefficient: No call to janus !
     * @param paasId
     * @param node
     * @param instance
     * @param iinfo
     * @param on
     */
    private void doSwitchInstanceMaintenanceMode(String paasId, String node, String instance, InstanceInformation iinfo, boolean on) {
        if (on && iinfo.getInstanceStatus() == InstanceStatus.SUCCESS) {
            log.debug(String.format("switching instance MaintenanceMode ON for node <%s>, instance <%s>", node, instance));
            updateInstanceState(paasId, node, instance, iinfo, "maintenance");
        } else if (!on && iinfo.getInstanceStatus() == InstanceStatus.MAINTENANCE) {
            log.debug(String.format("switching instance MaintenanceMode OFF for node <%s>, instance <%s>", node, instance));
            updateInstanceState(paasId, node, instance, iinfo, "started");
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
                InstanceInformation newInstanceInformation = newInstance(i);
                instanceInformations.put(String.valueOf(i), newInstanceInformation);
            }
        }
        return currentInformations;
    }

    private InstanceInformation newInstance(int i) {
        Map<String, String> attributes = Maps.newHashMap();
        Map<String, String> runtimeProperties = Maps.newHashMap();
        Map<String, String> outputs = Maps.newHashMap();
        return new InstanceInformation(ToscaNodeLifecycleConstants.INITIAL, InstanceStatus.PROCESSING, attributes, runtimeProperties, outputs);
    }

    private ScalingPolicy getScalingPolicy(String id, Map<String, NodeTemplate> nodeTemplates) {
        // Get the scaling of parent if not exist
        Capability scalableCapability = TopologyUtils.getScalableCapability(nodeTemplates, id, false);
        if (scalableCapability != null) {
            return TopologyUtils.getScalingPolicy(scalableCapability);
        }
        if (nodeTemplates.get(id).getRelationships() != null) {
            for (RelationshipTemplate rel : nodeTemplates.get(id).getRelationships().values()) {
                RelationshipType relType = getRelationshipType(rel.getType());
                if (ToscaUtils.isFromType(NormativeRelationshipConstants.HOSTED_ON, relType)) {
                    return getScalingPolicy(rel.getTarget(), nodeTemplates);
                }
            }
        }
        return null;
    }

    private RelationshipType getRelationshipType(String typeName) {
        return toscaTypeSearchService.findMostRecent(RelationshipType.class, typeName);
    }

    /**
     * Listen logs from Janus about this deployment
     * @param ctx
     */
    private void listenJanusLog(PaaSDeploymentContext ctx) {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("listenJanusLog " + paasId);
        final JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("listenJanusLog: no JanusRuntimeDeploymentInfo");
            return;
        }

        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    LogResponse logResponse = restClient.getLogFromJanus(deploymentUrl, prevIndex);
                    if (logResponse != null) {
                        prevIndex = logResponse.getLast_index();
                        if (logResponse.getLogs() != null) {
                            for (LogEvent logEvent : logResponse.getLogs()) {
                                log.debug("Received log from janus: " + logEvent.getLogs());
                                // add Premium Log
                                PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
                                deploymentLog.setContent(logEvent.getLogs());
                                deploymentLog.setTimestamp(logEvent.getDate());
                                postLog(deploymentLog, paasId);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    log.error("listenJanusLog Stopped " + paasId);
                    return;
                } catch (Exception e) {
                    log.warn("listenJanusLog Failed " + paasId, e);
                    return;
                }
            }
        };
        jrdi.getExecutor().submit(task);
    }

    /**
     * Listen events from Janus about this deployment
     * @param ctx
     */
    private void listenDeploymentEvent(PaaSDeploymentContext ctx) {
        String paasId = ctx.getDeploymentPaaSId();
        Deployment deployment = ctx.getDeployment();
        String source = deployment.getSourceName();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("listenDeploymentEvent " + paasId + " source=" + source);
        final JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("listenDeploymentEvent: no JanusRuntimeDeploymentInfo");
            return;
        }
        Map<String, Map<String, InstanceInformation>> instanceInfo = jrdi.getInstanceInformations();
        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    EventResponse eventResponse = restClient.getEventFromJanus(deploymentUrl, prevIndex);
                    if (eventResponse != null) {
                        prevIndex = eventResponse.getLast_index();
                        if (eventResponse.getEvents() != null) {
                            for (Event event : eventResponse.getEvents()) {
                                // Check type of Event sent by janus and process it
                                String eState = event.getStatus();
                                String eMessage = paasId + " - Janus Event: ";

                                if (event.getType() == null) {
                                    log.warn("Janus version is obsolete. Please use a newer version");
                                    event.setType("instance");
                                }

                                switch (event.getType()) {
                                    case "instance":
                                        String eNode = event.getNode();
                                        String eInstance = event.getInstance();
                                        eMessage += "instance " + eNode + ":" + eInstance + ":" + eState;
                                        log.debug(eMessage);
                                        Map<String, InstanceInformation> ninfo = instanceInfo.get(eNode);
                                        if (ninfo == null) {
                                            // Add a new Node in JanusRuntimeDeploymentInfo
                                            log.debug("Add a node in JanusRuntimeDeploymentInfo: " + eNode);
                                            ninfo = Maps.newHashMap();
                                            instanceInfo.put(eNode, ninfo);
                                        }
                                        InstanceInformation iinfo = ninfo.get(eInstance);
                                        if (iinfo == null) {
                                            // Add a new Instance for this node in JanusRuntimeDeploymentInfo
                                            log.debug("Add an instance in JanusRuntimeDeploymentInfo: " + eInstance);
                                            iinfo = newInstance(new Integer(eInstance));
                                            ninfo.put(eInstance, iinfo);
                                        }
                                        updateInstanceState(paasId, eNode, eInstance, iinfo, eState);
                                        switch (eState) {
                                            case "initial":
                                            case "creating":
                                            case "deleting":
                                            case "starting":
                                            case "configured":
                                            case "configuring":
                                                break;
                                            case "deleted":
                                                ninfo.remove(eInstance);
                                                break;
                                            case "started":
                                                updateInstanceAttributes(ctx, iinfo, eNode, eInstance);
                                                // persist BS Id
                                                if (source.equals("BLOCKSTORAGE_APPLICATION")) {
                                                    PaaSInstancePersistentResourceMonitorEvent prme = new PaaSInstancePersistentResourceMonitorEvent(eNode, eInstance,
                                                            MapUtil.newHashMap(new String[]{NormativeBlockStorageConstants.VOLUME_ID}, new Object[]{UUID.randomUUID().toString()}));
                                                    postEvent(prme, paasId);
                                                }
                                                break;
                                            case "error":
                                                break;
                                            default:
                                                log.warn("Unknown instance status: " + eState);
                                                break;
                                        }
                                        break;
                                    case "deployment":
                                    case "custom_command":
                                    case "workflow":
                                    case "scaling":
                                        eMessage += event.getType() + ":" + eState;
                                        log.debug(eMessage);
                                        synchronized (jrdi) {
                                            if (jrdi.getLastEvent() != null) {
                                                log.debug("Event not taken, forgot it: " + jrdi.getLastEvent());
                                            }
                                            jrdi.setLastEvent(event);
                                            jrdi.notifyAll();
                                        }
                                        break;
                                    default:
                                        log.warn("Unknown event type received from janus: " + event.getType());
                                        break;
                                }
                            }
                        }
                    }
                } catch (JanusRestException e) {
                    int error = e.getHttpStatusCode();
                    if (error == 404) {
                        // Assume undeployed OK.
                        // Janus may not have returned the event "undeployed", so emulate it.
                        log.debug("JanusRestException error 404: assumes undeployed");
                        Event event = new Event();
                        event.setType("deployment");
                        event.setStatus("undeployed");
                        synchronized (jrdi) {
                            if (jrdi.getLastEvent() != null) {
                                log.debug("Event not taken, forgot it: " + jrdi.getLastEvent());
                            }
                            jrdi.setLastEvent(event);
                            jrdi.notifyAll();
                        }
                    } else {
                        log.error("listenDeploymentEvent Failed " + paasId, e);
                    }
                    return;
                } catch (InterruptedException e) {
                    log.error("listenDeploymentEvent Stopped " + paasId);
                    return;
                } catch (Exception e) {
                    log.warn("listenDeploymentEvent Failed " + paasId, e);
                    return;
                }
            }
        };
        jrdi.getExecutor().submit(task);
    }

    /**
     * return Instance Status from the instance state
     * See janus/tosca/states.go (_NodeState_name) but other states may exist for custom commands
     * @param state
     * @return
     */
    private static InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
            case "started":
                return InstanceStatus.SUCCESS;
            case "deleted":
                return null;
            case "error":
                return InstanceStatus.FAILURE;
            default:
                return InstanceStatus.PROCESSING;
        }
    }

    /**
     * Post an Event for alien4cloud
     * @param event AbstractMonitorEvent
     * @param paasId
     */
    protected void postEvent(AbstractMonitorEvent event, String paasId) {
        event.setDate((new Date()).getTime());
        event.setDeploymentId(a4cDeploymentIds.get(paasId));
        event.setOrchestratorId(paasId);
        if (event.getDeploymentId() == null) {
            log.error("Must provide an Id for this Event: " + event.toString());
            Thread.dumpStack();
            return;
        }
        synchronized (toBeDeliveredEvents) {
            toBeDeliveredEvents.add(event);
        }
    }

    /**
     * Post a PaaSDeploymentLog to a4c premium log
     * @param pdlog
     * @param paasId
     */
    protected void postLog(PaaSDeploymentLog pdlog, String paasId) {
        pdlog.setDeploymentId(a4cDeploymentIds.get(paasId));
        pdlog.setDeploymentPaaSId(paasId);
        if (pdlog.getDeploymentId() == null) {
            log.error("Must provide an Id for this log: " + pdlog.toString());
            Thread.dumpStack();
            return;
        }
        pdlog.setLevel(PaaSDeploymentLogLevel.INFO);
        alienMonitorDao.save(pdlog);
    }

    /**
     * Maps janus DeploymentStatus in alien4cloud DeploymentStatus.
     * See janus/deployments/structs.go to see all possible values
     * @param state
     * @return
     */
    private static DeploymentStatus getDeploymentStatusFromString(String state) {
        switch (state) {
            case "DEPLOYED":
                return DeploymentStatus.DEPLOYED;
            case "UNDEPLOYED":
                return DeploymentStatus.UNDEPLOYED;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                return DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
            case "UNDEPLOYMENT_IN_PROGRESS":
                return DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
            case "INITIAL":
                return DeploymentStatus.INIT_DEPLOYMENT;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
                return DeploymentStatus.FAILURE;
            default:
                return DeploymentStatus.UNKNOWN;
        }
    }
}
