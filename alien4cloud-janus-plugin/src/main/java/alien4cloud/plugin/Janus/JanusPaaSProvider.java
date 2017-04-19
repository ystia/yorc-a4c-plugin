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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.inject.Inject;

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
import alien4cloud.plugin.Janus.baseplugin.AbstractPaaSProvider;
import alien4cloud.plugin.Janus.rest.JanusRestException;
import alien4cloud.plugin.Janus.rest.Response.AttributeResponse;
import alien4cloud.plugin.Janus.rest.Response.DeployInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.Response.EventResponse;
import alien4cloud.plugin.Janus.rest.Response.InstanceInfosResponse;
import alien4cloud.plugin.Janus.rest.Response.Link;
import alien4cloud.plugin.Janus.rest.Response.LogEvent;
import alien4cloud.plugin.Janus.rest.Response.LogResponse;
import alien4cloud.plugin.Janus.rest.Response.NodeInfosResponse;
import alien4cloud.plugin.Janus.rest.RestClient;
import alien4cloud.plugin.Janus.utils.MappingTosca;
import alien4cloud.plugin.Janus.utils.ShowTopology;
import alien4cloud.plugin.Janus.utils.ZipTopology;
import alien4cloud.plugin.Janus.workflow.WorkflowPlayer;
import alien4cloud.plugin.Janus.workflow.WorkflowReader;
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

/**
 * Janus dependant part of the a4c janus plugin
 */
@Slf4j
public abstract class JanusPaaSProvider extends AbstractPaaSProvider {
    private static final String BLOCKSTORAGE_APPLICATION = "BLOCKSTORAGE-APPLICATION";
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final Map<String, JanusRuntimeDeploymentInfo> runtimeDeploymentInfos = Maps.newConcurrentMap();
    private final List<AbstractMonitorEvent> toBeDeliveredEvents = Collections.synchronizedList(new ArrayList<AbstractMonitorEvent>());
    private ProviderConfig providerConfiguration;
    private Map<String, String> a4cDeploymentIds = Maps.newHashMap();

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    private ZipTopology zipTopology = new ZipTopology();

    private RestClient restClient = RestClient.getInstance();

    private ArchiveExportService archiveExportService = new ArchiveExportService ();

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;


    @PreDestroy
    public void destroy() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public DeploymentStatus doGetStatus(String deploymentPaaSId) {
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(deploymentPaaSId);
        if (jrdi == null) {
            return DeploymentStatus.UNDEPLOYED;
        }
        return jrdi.getStatus();
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
        if (scalableCapability == null) {
            if (nodeTemplates.get(id).getRelationships() != null) {
                for (RelationshipTemplate rel : nodeTemplates.get(id).getRelationships().values()) {
                    RelationshipType relType = getRelationshipType(rel.getType());
                    if (ToscaUtils.isFromType(NormativeRelationshipConstants.HOSTED_ON, relType)) {
                        return getScalingPolicy(rel.getTarget(), nodeTemplates);
                    }
                }
            } else {
                return null;
            }
        } else {
            return TopologyUtils.getScalingPolicy(scalableCapability);
        }
        return null;
    }


    private Map<String, Map<String, InstanceInformation>> setupInstanceInformations(Topology topology) {
        log.debug("setupInstanceInformations for " + topology.getArchiveName() + " : " + topology.getArchiveVersion());
        Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
        if (nodeTemplates == null) {
            nodeTemplates = Maps.newHashMap();
        }
        Map<String, Map<String, InstanceInformation>> currentInformations = Maps.newHashMap();
        for (Map.Entry<String, NodeTemplate> nodeTemplateEntry : nodeTemplates.entrySet()) {
            Map<String, InstanceInformation> instanceInformations = Maps.newHashMap();
            currentInformations.put(nodeTemplateEntry.getKey(), instanceInformations);
            ScalingPolicy policy = getScalingPolicy(nodeTemplateEntry.getKey(), nodeTemplates);
            int initialInstances = policy != null ? policy.getInitialInstances() : 1;
            for (int i = 0; i < initialInstances; i++) {
                InstanceInformation newInstanceInformation = this.newInstance(i);
                instanceInformations.put(String.valueOf(i), newInstanceInformation);
            }
        }

        return currentInformations;
    }

    @Override
    protected synchronized void doDeploy(final PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        String paasId =  deploymentContext.getDeploymentPaaSId();
        log.debug("Deploying deployment [" + paasId + "] having id : " + deploymentContext.getDeploymentId());
        this.a4cDeploymentIds.put(paasId, deploymentContext.getDeploymentId());

        Topology topology = deploymentContext.getDeploymentTopology();

        Map<String, Map<String, InstanceInformation>> currentInformations = this.setupInstanceInformations(topology);


        JanusRuntimeDeploymentInfo jrdi =
                new JanusRuntimeDeploymentInfo(deploymentContext, DeploymentStatus.INIT_DEPLOYMENT, currentInformations, "");
        runtimeDeploymentInfos.put(paasId, jrdi);

        doChangeStatus(paasId, DeploymentStatus.DEPLOYMENT_IN_PROGRESS);

        MappingTosca.addPreConfigureSteps(topology, deploymentContext.getPaaSTopology());
        MappingTosca.generateOpenstackFIP(deploymentContext);

        // Create the yml of our topology (after substitution)
        Csar myCsar = new Csar(paasId, topology.getArchiveVersion());
        String yaml = archiveExportService.getYaml(myCsar, topology);
        List<String> lines = Collections.singletonList(yaml);
        log.debug("YML Topology");
        Path file = Paths.get("topology.yml");
        try {
            Files.write(file, lines, Charset.forName("UTF-8"));
        } catch (IOException e) {
            doChangeStatus(paasId, DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }

        // Build our zip topology
        try {
            File zip = new File("topology.zip");
            log.debug("ZIP Topology");
            zipTopology.buildZip(zip, deploymentContext);
        } catch (IOException e) {
            doChangeStatus(paasId, DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }

        //put topology zip to Janus
        log.info("PUT Topology");

        String deploymentUrl;
        try {
            deploymentUrl = restClient.putTopologyToJanus(paasId);
        } catch (Exception e) {
            doChangeStatus(paasId, DeploymentStatus.FAILURE);
            callback.onFailure(e);
            return;
        }
        jrdi.setDeploymentUrl(deploymentUrl);
        log.debug("Deployment Url : " + deploymentUrl);
        sendMessage(paasId, deploymentUrl);

        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            log.info("Running another thread for event check " + threadName);

            try {
                checkJanusStatusUntil("DEPLOYED", deploymentUrl);
                this.changeStatus(paasId, DeploymentStatus.DEPLOYED);
                callback.onSuccess(null);
            } catch (Exception e) {
                this.changeStatus(paasId, DeploymentStatus.FAILURE);
                // runtimeDeploymentInfos.remove(name);
                callback.onFailure(e);
            }
        };
        Thread thread = new Thread(task);
        thread.start();

        this.listenDeploymentEvent(deploymentContext);

        this.listenJanusLog(deploymentUrl, deploymentContext);
    }

    /**
     * Scale a Node
     *
     * @param ctx    the deployment context
     * @param nodeId id of the compute node to scale up
     * @param nbi    the number of instances to be added (if positive) or removed (if negative)
     */
    @Override
    public void doScale(PaaSDeploymentContext ctx, String nodeId, int nbi, IPaaSCallback<?> callback) {
        log.info("scaling " + nodeId + " delta=" + nbi);
        String paasId = ctx.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        String deploymentUrl = jrdi.getDeploymentUrl();
        Map<String, Map<String, InstanceInformation>> einfo = jrdi.getInstanceInformations();
        Map<String, InstanceInformation> nodeInfo = einfo.get(nodeId);
        if (nodeInfo != null) {
            int currentSize = nodeInfo.size();
            log.debug("current size : " + currentSize);
        }
        String taskUrl = null;
        try {
            taskUrl = this.restClient.scaleNodeInJanus(deploymentUrl, nodeId, nbi);
        } catch (Exception e) {
            callback.onFailure(e);
            return;
        }

        // Check status DONE after scaling
        final String url = taskUrl;
        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            log.debug("Running another thread for event check " + threadName);
            try {
                checkJanusStatusUntil("DONE", url);
                // TODO replace this by actions in listenDeploymentEvent
                updateNodeInfo(ctx, nodeId, null);
                callback.onSuccess(null);
            } catch (Exception e) {
                e.printStackTrace();
                this.changeStatus(ctx.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
                sendMessage(ctx.getDeploymentPaaSId(), e.getMessage());
                callback.onFailure(e);
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    /**
     * Update Deployment Info from Janus information
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
            updateNodeInfo(ctx, null, null);
        } catch (Exception e) {
            log.error("Cannot get info for deployment " + ctx.getDeploymentPaaSId(), e);
        }
    }

    /**
     * Update nodeInformation in the JanusRuntimeDeploymentInfo
     * This is needed to let a4c know all about the nodes and their instances
     * Information is got from Janus using the REST API
     *
     * @param ctx PaaSDeploymentContext to be updated
     * @param nodeName null = All nodes
     * @param instanceName null = All instances of this node
     *
     * @throws
     */
    private void updateNodeInfo(PaaSDeploymentContext ctx, final String nodeName, final String instanceName) throws Exception {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("updateNodeInfo " + paasId + " " + nodeName + " " + instanceName);

        // Assumes JanusRuntimeDeploymentInfo already created.
        JanusRuntimeDeploymentInfo jrdi = this.runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("No JanusRuntimeDeploymentInfo");
            return;
        }

        // Find the deployment info from Janus
        DeployInfosResponse deployRes = this.restClient.getDeploymentInfosFromJanus(deploymentUrl);
        jrdi.setStatus(getDeploymentStatusFromString(deployRes.getStatus()));

        Map<String, Map<String, InstanceInformation>> nodemap = jrdi.getInstanceInformations();

        // Look every node we want to update.
        for (Link nodeLink : deployRes.getLinks()) {
            if (nodeLink.getRel().equals("node")) {
                // nodeName is the last part of nodeLink.getHref()
                if (nodeName == null || nodeLink.getHref().endsWith(nodeName)) {

                    // Find the node info from Janus
                    NodeInfosResponse nodeInfosRes = this.restClient.getNodesInfosFromJanus(nodeLink.getHref());

                    Map<String, InstanceInformation> instanceMap = nodemap.get(nodeInfosRes.getName());
                    if (instanceMap == null) {
                        // This node was unknown. Create it.
                        instanceMap = Maps.newHashMap();
                        nodemap.put(nodeInfosRes.getName(), instanceMap);
                    }
                    // Find information about all the node instances from Janus
                    for (Link instanceLink : nodeInfosRes.getLinks()) {
                        if (instanceLink.getRel().equals("instance")) {
                            if (instanceName == null || instanceLink.getHref().endsWith(instanceName)) {

                                // Find the instance info from Janus
                                InstanceInfosResponse instInfoRes = this.restClient.getInstanceInfosFromJanus(instanceLink.getHref());

                                String inb = instInfoRes.getId();
                                InstanceInformation iinfo = instanceMap.get(inb);
                                if (iinfo == null) {
                                    // This instance was unknown. create it.
                                    iinfo = newInstance(new Integer(inb));
                                    instanceMap.put(inb, iinfo);
                                }
                                for (Link link : instInfoRes.getLinks()) {
                                    switch (link.getRel()) {
                                        case "attribute":
                                            // Get the attribute from Janus
                                            AttributeResponse attrRes = this.restClient.getAttributeFromJanus(link.getHref());
                                            iinfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                                            log.debug("Attribute: " + attrRes.getName() + "=" + attrRes.getValue());
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Listen events from Janus about this deployment
     * TODO process all events from janus (end of task, ...)
     * @param ctx
     */
    private void listenDeploymentEvent(PaaSDeploymentContext ctx) {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("listenDeploymentEvent " + paasId);
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
                    EventResponse eventResponse = this.restClient.getEventFromJanus(deploymentUrl, prevIndex);
                    if (eventResponse == null) {
                        TimeUnit.SECONDS.sleep(2);
                        continue;
                    }
                    prevIndex = eventResponse.getLast_index();
                    if (eventResponse.getEvents() != null) {
                        for (Event event : eventResponse.getEvents()) {
                            // An event about Instance state has been sent by Janus
                            String eNode =  event.getNode();
                            String eInstance = event.getInstance();
                            String eState = event.getStatus();
                            String eMessage = eNode + ":" + eInstance + ":" + eState;
                            log.debug("Received janus event " + eMessage);
                            this.sendMessage(paasId, "[listenDeploymentEvent] " + eMessage);

                            Map<String, InstanceInformation> nodeInstancesInfos = instanceInfo.get(eNode);
                            if (nodeInstancesInfos == null) {
                                // Add a new Node in JanusRuntimeDeploymentInfo
                                log.debug("[listenDeploymentEvent] nodeInstancesInfos == null");
                                nodeInstancesInfos = Maps.newHashMap();
                                instanceInfo.put(eNode, nodeInstancesInfos);
                            }

                            if (event.getStatus().equals("started")) {
                                // TODO call this even if not "started" ?
                                updateNodeInfo(ctx, eNode, eInstance);
                            }

                            InstanceInformation iinfo = nodeInstancesInfos.get(eInstance);
                            if (iinfo == null) {
                                // Add a new Instance for this node in JanusRuntimeDeploymentInfo
                                log.debug("[listenDeploymentEvent] creating instance info for " + eInstance);
                                iinfo = newInstance(new Integer(eInstance));
                                nodeInstancesInfos.put(eInstance, iinfo);
                            }
                            if (event.getStatus().equals("deleted")) {
                                // Remove instance for this node in JanusRuntimeDeploymentInfo
                                log.debug("[listenDeploymentEvent] remove instance " + eInstance);
                                nodeInstancesInfos.remove(eInstance);
                            }
                            updateInstanceState(paasId, eNode, eInstance, iinfo, eState);
                        }
                    }
                } catch (InterruptedException e) {
                    String threadName = Thread.currentThread().getName();
                    log.error("[listenDeploymentEvent] Stopped " + threadName + " " + paasId);
                    return;
                } catch (Exception e) {
                    log.warn("[listenDeploymentEvent] Failed " + paasId, e);
                    return;
                }
            }
        };
        this.runtimeDeploymentInfos.get(paasId).getExecutor().submit(task);
    }


    private void addPremiumLog(PaaSDeploymentContext deploymentContext, String mylog, Date date, PaaSDeploymentLogLevel level) {
        Deployment deployment = deploymentContext.getDeployment();
        PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
        deploymentLog.setDeploymentId(deployment.getId());
        deploymentLog.setDeploymentPaaSId(deployment.getOrchestratorDeploymentId());
        deploymentLog.setContent(mylog);
        deploymentLog.setLevel(level);
        deploymentLog.setTimestamp(date);
        alienMonitorDao.save(deploymentLog);
    }

    private void listenJanusLog(String deploymentUrl, PaaSDeploymentContext deploymentContext) {
        log.debug("listenJanusLog");
        String deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    LogResponse logResponse = this.restClient.getLogFromJanus(deploymentUrl, prevIndex);
                    if (logResponse == null) {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }
                    prevIndex = logResponse.getLast_index();
                    if (logResponse.getLogs() != null) {
                        for (LogEvent logEvent : logResponse.getLogs()) {
                            log.debug("Received log from janus: " + logEvent.getLogs());
                            this.sendMessage(deploymentPaaSId, logEvent.getLogs());
                            addPremiumLog(deploymentContext, logEvent.getLogs(), logEvent.getDate(), PaaSDeploymentLogLevel.INFO);
                        }
                    }
                } catch (InterruptedException e) {
                    String threadName = Thread.currentThread().getName();
                    log.info("[listenJanusLog] Stopped " + threadName + " " + deploymentPaaSId);
                    return;
                } catch (JanusRestException e) {
                    if (e.getHttpStatusCode() == 404) {
                        log.info("[listenJanusLog] Stopped got 404 exception " + deploymentPaaSId);
                        return;
                    }
                    e.printStackTrace();
                } catch (Exception e) {
                    log.info("getLogFromJanus raise exception: " + e);
                }
            }

        };
        this.runtimeDeploymentInfos.get(deploymentPaaSId).getExecutor().submit(task);
    }

    private void checkJanusStatusUntil(String aimStatus, String deploymentUrl) throws Exception {
        log.debug("checkJanusStatusUntil " + aimStatus + " URL=" + deploymentUrl);
        String status = "";
        while (!status.equals(aimStatus)) {
            try {
                status = restClient.getStatusFromJanus(deploymentUrl);
                if (status.contains("FAILED")) {
                    log.info("Operation has failed");
                    throw(new Exception("Operation failed"));
                }
            }
            catch (Exception e) {
                if (aimStatus.equals("UNDEPLOYED")) {
                    // Assumes application has been destroyed, triggering this Exception
                    log.info("Assumes Undeployment is OK");
                    return;
                } else {
                    // An error occured. Rethrow the Exception
                    log.debug("checkJanusStatusUntil " + aimStatus + " raised exception: ", e);
                    throw(e);
                }
            }
            log.debug("[checkJanusStatusUntil] current status: " + status);
            Thread.sleep(4000);
        }
    }

    /**
     * Undeploy a deployment.
     * @param deploymentContext
     */
    @Override
    protected synchronized void doUndeploy(final PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        final String paasId = deploymentContext.getDeploymentPaaSId();

        doChangeStatus(paasId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);

        Runnable task = () -> {
            try {
                String deploymentUrl = runtimeDeploymentInfos.get(paasId).getDeploymentUrl();
                restClient.undeployJanus(deploymentUrl);
                checkJanusStatusUntil("UNDEPLOYED", deploymentUrl);
            } catch (Exception e) {
                sendMessage(paasId, e.getMessage());
                changeStatus(paasId, DeploymentStatus.FAILURE);
                callback.onFailure(e);
            }

            changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
            // cleanup deployment cache
            runtimeDeploymentInfos.get(paasId).getExecutor().shutdownNow();
            runtimeDeploymentInfos.remove(paasId);
            callback.onSuccess(null);
        };

        Thread thread = new Thread(task);
        thread.start();
    }

    /**
     * Change status of the deployment in JanusRuntimeDeploymentInfo
     * This must be called with providerLock
     * @param paasId
     * @param status
     * @return old status
     */
    @Override
    protected synchronized DeploymentStatus doChangeStatus(final String paasId, final DeploymentStatus status) {
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        DeploymentStatus oldDeploymentStatus = jrdi.getStatus();
        log.info("Deployment [" + paasId + "] moved from status [" + oldDeploymentStatus + "] to [" + status + "]");
        jrdi.setStatus(status);

        PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
        event.setDeploymentStatus(status);
        event.setDate((new Date()).getTime());
        event.setDeploymentId(a4cDeploymentIds.get(paasId));
        event.setOrchestratorId(paasId);
        toBeDeliveredEvents.add(event);

        return oldDeploymentStatus;
    }

    /**
     * Deliver a PaaSMessageMonitorEvent to alien4cloud
     * @param paasId
     * @param message
     */
    protected synchronized void sendMessage(final String paasId, final String message) {
        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate((new Date()).getTime());
        messageMonitorEvent.setDeploymentId(a4cDeploymentIds.get(paasId));
        messageMonitorEvent.setOrchestratorId(paasId);
        messageMonitorEvent.setMessage(message);
        if (messageMonitorEvent.getDeploymentId() == null) {
            log.error("Must provide an Id for this PaaSMessageMonitorEvent: " + messageMonitorEvent.toString());
            Thread.dumpStack();
            return;
        }
        toBeDeliveredEvents.add(messageMonitorEvent);
    }

    /**
     * Update Instance State and notify alien4cloud if needed
     * @param paasId Deployment PaaS Id
     * @param nodeId
     * @param instanceId
     * @param iinfo
     * @param state
     */
    private void updateInstanceState(String paasId, String nodeId, String instanceId, InstanceInformation iinfo, String state) {
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
        event.setDate((new Date()).getTime());
        event.setDeploymentId(a4cDeploymentIds.get(paasId));
        event.setOrchestratorId(paasId);
        event.setRuntimeProperties(iinfo.getRuntimeProperties());
        event.setAttributes(iinfo.getAttributes());
        toBeDeliveredEvents.add(event);

        final JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        Deployment deployment = jrdi.getDeploymentContext().getDeployment();
        if (deployment.getSourceName().equals(BLOCKSTORAGE_APPLICATION) && state.equalsIgnoreCase("created")) {
            PaaSInstancePersistentResourceMonitorEvent prme = new PaaSInstancePersistentResourceMonitorEvent(nodeId, instanceId,
                    MapUtil.newHashMap(new String[]{NormativeBlockStorageConstants.VOLUME_ID}, new Object[]{UUID.randomUUID().toString()}));
            prme.setDeploymentId(deployment.getId());
            prme.setOrchestratorId(paasId);
            toBeDeliveredEvents.add(prme);
        }

    }

    private RelationshipType getRelationshipType(String typeName) {
        return toscaTypeSearchService.findMostRecent(RelationshipType.class, typeName);
    }


    /**
     * Return instances information to alien4cloud.
     * @param deploymentContext the deployment context
     * @param callback callback when the information will be available
     */
    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        log.debug("getInstancesInformation");
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());
        if (jrdi != null) {
            callback.onSuccess(jrdi.getInstanceInformations());
        } else {
            log.warn("No information about this deployment: " + deploymentContext.getDeploymentPaaSId());
            log.warn("Assuming that it has been undeployed");
            callback.onSuccess(Maps.newHashMap());
        }
    }

    /**
     * Return events to alien4cloud
     * Called from a4c every second
     * TODO parameters date and maxevents should be used ?
     * @param date The start date since which we should retrieve events.
     * @param maxEvents The maximum number of events to return.
     * @param eventsCallback
     */
    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventsCallback) {
        for (AbstractMonitorEvent evt : toBeDeliveredEvents) {
            log.debug("a4c will process event: " + evt.toString());
        }
        AbstractMonitorEvent[] events = toBeDeliveredEvents.toArray(new AbstractMonitorEvent[toBeDeliveredEvents.size()]);
        toBeDeliveredEvents.clear();
        eventsCallback.onSuccess(events);
    }

    /**
     * Execute an operation (custom command)
     * @param deploymentContext the deployment context in which operation is to be executed
     * @param request containes operation description and parameters
     */
    @Override
    protected void doExecuteOperation(PaaSDeploymentContext deploymentContext, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback) {
        log.info("Do execute " + request.getOperationName() + " on node " + request.getNodeTemplateName());

        String deploymentUrl = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getDeploymentUrl();
        String taskUrl = null;
        try {
            taskUrl = restClient.postCustomCommandToJanus(deploymentUrl, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Check status DONE after executing operation
        final String url = taskUrl;
        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            log.info("Running another thread for event check " + threadName);
            try {
                checkJanusStatusUntil("DONE", url);

                Map<String, String> customResults = null;
                customResults = new Hashtable<>(1);
                customResults.put("result", "Succesfully executed custom " + request.getOperationName() + " on node " + request.getNodeTemplateName());
                // TODO Get results returned by the custom command ??
                callback.onSuccess(customResults);
            } catch (Exception e) {
                sendMessage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
                callback.onFailure(e);
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    /**
     * Execute a workflow
     * @param ctx he deployment context in which the workflow is to be executed
     * @param workflowName the name of the workflow to execute
     * @param inputs parameters for the workflow
     * @param callback allow to communicate with Alien UI
     */
    @Override
    protected void doLaunchWorkflow(PaaSDeploymentContext ctx, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        log.info("Do execute workflow " + workflowName);

        String deploymentUrl = runtimeDeploymentInfos.get(ctx.getDeploymentPaaSId()).getDeploymentUrl();
        String taskUrl = null;
        try {
            taskUrl = restClient.postWorkflowToJanus(deploymentUrl, workflowName, inputs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Check status DONE after executing operation
        final String url = taskUrl;
        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            log.info("Running another thread for event check " + threadName);
            try {
                checkJanusStatusUntil("DONE", url);

                // Properties may have changed and must be read again
                updateNodeInfo(ctx, null, null);

                // Currently the workflow execution doesn't return results
                callback.onSuccess(null);
            } catch (Exception e) {
                sendMessage(ctx.getDeploymentPaaSId(), e.getMessage());
                callback.onFailure(e);
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    @Override
    public void setConfiguration(ProviderConfig configuration) throws PluginConfigurationException {
        log.info("set config for " + this.getClass().getName());
        this.providerConfiguration = configuration;
        this.restClient.setProviderConfiguration(this.providerConfiguration);
    }

    /**
     *
     * @param deploymentContext
     * @param on
     */
    @Override
    public void doSwitchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean on) {
        String  deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(deploymentPaaSId);
        if (jrdi == null) {
            log.error("No Deployment Information");
            return;
        }

        Topology topology = jrdi.getDeploymentContext().getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> nodes = jrdi.getInstanceInformations();
        if (nodes == null || nodes.isEmpty()) {
            return;
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
                            switchInstanceMaintenanceMode(deploymentPaaSId, node, instance, iinfo, on);
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * @param deploymentContext
     * @param node
     * @param instance
     * @param mode
     */
    @Override
    public void doSwitchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String node, String instance, boolean mode) {
        String  deploymentPaaSId = deploymentContext.getDeploymentPaaSId();
        JanusRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(deploymentPaaSId);
        if (jrdi == null) {
            log.error("No Deployment Information");
            return;
        }

        final Map<String, Map<String, InstanceInformation>> existingInformations = jrdi.getInstanceInformations();
        if (existingInformations != null && existingInformations.containsKey(node)
                && existingInformations.get(node).containsKey(instance)) {
            InstanceInformation iinfo = existingInformations.get(node).get(instance);
            switchInstanceMaintenanceMode(deploymentPaaSId, node, instance, iinfo, mode);
        }
    }

    private void switchInstanceMaintenanceMode(String deploymentPaaSId, String node, String instance, InstanceInformation iinfo, boolean on) {
        if (on && iinfo.getInstanceStatus() == InstanceStatus.SUCCESS) {
            log.debug(String.format("switching instance MaintenanceMode ON for node <%s>, instance <%s>", node, instance));
            updateInstanceState(deploymentPaaSId, node, instance, iinfo, "maintenance");
        } else if (!on && iinfo.getInstanceStatus() == InstanceStatus.MAINTENANCE) {
            log.debug(String.format("switching instance MaintenanceMode OFF for node <%s>, instance <%s>", node, instance));
            updateInstanceState(deploymentPaaSId, node, instance, iinfo, "started");
        }
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
            case "uninitialized":
            case "stopping":
            case "stopped":
            case "starting":
            case "configuring":
            case "configured":
            case "creating":
            case "created":
            case "deleting":
                return InstanceStatus.PROCESSING;
            case "deleted":
                return null;
            case "maintenance":
                return InstanceStatus.MAINTENANCE;
            default:
                return InstanceStatus.FAILURE;
        }
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
