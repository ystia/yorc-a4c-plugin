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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.topology.TopologyUtils;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
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

@Slf4j
public abstract class JanusPaaSProvider extends AbstractPaaSProvider {
    private static final String BLOCKSTORAGE_APPLICATION = "BLOCKSTORAGE-APPLICATION";
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final Map<String, JanusRuntimeDeploymentInfo> runtimeDeploymentInfos = Maps.newConcurrentMap();
    private final List<AbstractMonitorEvent> toBeDeliveredEvents = Collections.synchronizedList(new ArrayList<AbstractMonitorEvent>());
    private ProviderConfig providerConfiguration;
    private Map<String, String> paaSDeploymentIdToAlienDeploymentIdMap = Maps.newHashMap();
    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;
    private ShowTopology showTopology = new ShowTopology();

    private WorkflowReader workflowReader;

    private WorkflowPlayer workflowPlayer = new WorkflowPlayer();

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
    public DeploymentStatus doGetStatus(String deploymentPaaSId, boolean triggerEventIfUndeployed) {
        JanusRuntimeDeploymentInfo deploymentInfo = runtimeDeploymentInfos.get(deploymentPaaSId);
        if (deploymentInfo == null) {
            return DeploymentStatus.UNDEPLOYED;
        }
        return deploymentInfo.getStatus();
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


    private Map<String, Map<String, InstanceInformation>> setupInstanceInformations(final PaaSTopologyDeploymentContext deploymentContext, Topology topology) {

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
    protected synchronized void doDeploy(final PaaSTopologyDeploymentContext deploymentContext) {
        log.info("Deploying deployment [" + deploymentContext.getDeploymentPaaSId() + "]");
        this.paaSDeploymentIdToAlienDeploymentIdMap.put(deploymentContext.getDeploymentPaaSId(), deploymentContext.getDeploymentId());

        Topology topology = deploymentContext.getDeploymentTopology();

        Map<String, Map<String, InstanceInformation>> currentInformations = this.setupInstanceInformations(deploymentContext, topology);


        // Why DeploymentStatus.DEPLOYMENT_IN_PROGRESS and not DeploymentStatus.INIT_DEPLOYMENT ??
        JanusRuntimeDeploymentInfo janusDeploymentInfo = new JanusRuntimeDeploymentInfo(deploymentContext, DeploymentStatus.DEPLOYMENT_IN_PROGRESS, currentInformations, "");
        runtimeDeploymentInfos.put(deploymentContext.getDeploymentPaaSId(), janusDeploymentInfo);


        this.changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.DEPLOYMENT_IN_PROGRESS);

        MappingTosca.addPreConfigureSteps(topology, deploymentContext.getPaaSTopology());

        MappingTosca.generateOpenstackFIP(deploymentContext);

        //Create the yml of our topology (after substitution)
        String yaml = archiveExportService.getYaml(new Csar(), topology);
        //log.info(yaml);
        List<String> lines = Collections.singletonList(yaml);
        log.info("YML Topology");
        Path file = Paths.get("topology.yml");
        try {
            Files.write(file, lines, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Build our zip topology
        try {
            File zip = new File("topology.zip");
            log.info("ZIP Topology");
            zipTopology.buildZip(zip, deploymentContext);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //doChangeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYED);

        //post topology zip to Janus
        log.info("POST Topology");


        String deploymentUrl;
        try {
            deploymentUrl = restClient.postTopologyToJanus();
        } catch (Exception e) {
            e.printStackTrace();
            this.changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
            sendMesage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
            return;
        }
        janusDeploymentInfo.setDeploymentUrl(deploymentUrl);
        log.info("Deployment Url : " + deploymentUrl);
        sendMesage(deploymentContext.getDeploymentPaaSId(), deploymentUrl);


        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            log.info("Running another thread for event check " + threadName);

            try {

                checkJanusStatusUntil("DEPLOYED", deploymentUrl);

                this.changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.DEPLOYED);

                this.setAttributes(deploymentUrl, deploymentContext);

            } catch (Exception e) {
                e.printStackTrace();
                this.changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
                sendMesage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
                this.changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYED);
                runtimeDeploymentInfos.remove(deploymentContext.getDeploymentPaaSId());

                throw new RuntimeException(e.getMessage()); // TODO : Refactor, For detecting error deploy rest API A4C, when integrationt test
            }
        };

        Thread thread = new Thread(task);
        thread.start();

        this.listenDeploymentEvent(deploymentUrl, deploymentContext.getDeploymentPaaSId());
        this.listenJanusLog(deploymentUrl, deploymentContext);
    }

    private void setAttributes(String deploymentUrl, PaaSDeploymentContext deploymentContext) throws Exception {
        Map<String, Map<String, InstanceInformation>> intancesInfos =  this.runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getInstanceInformations();
        DeployInfosResponse deployRes =  this.restClient.getDeploymentInfosFromJanus(deploymentUrl);

        List<Link> nodes = deployRes.getLinks().stream().filter(link -> link.getRel().equals("node")).collect(Collectors.toList());
        for(Link nodeLink : nodes) {
            NodeInfosResponse nodeInfosRes = this.restClient.getNodesInfosFromJanus(nodeLink.getHref());

            for(Link instanceLink : nodeInfosRes.getLinks()) {
                if(instanceLink.getRel().equals("instance")) {
                    AtomicBoolean nodeFound = new AtomicBoolean(true);
                    InstanceInfosResponse instInfoRes = this.restClient.getInstanceInfosFromJanus(instanceLink.getHref());
                    instInfoRes.getLinks().stream().filter(attributeLink -> attributeLink.getRel().equals("attribute")).forEach(attributeLink -> {
                        try {
                            AttributeResponse attrRes = this.restClient.getAttributeFromJanus(attributeLink.getHref());
                            log.info("--------------------------------");
                            log.info("{}",attrRes);
                            Map<String, InstanceInformation> nodeInstancesInfos = intancesInfos.get(nodeInfosRes.getName());
                            if (nodeInstancesInfos == null) {
                                nodeFound.set(false);
                                return;
                            }
                            InstanceInformation instanceInfo = nodeInstancesInfos.get(instInfoRes.getId());
                            if (nodeInstancesInfos == null) {
                                nodeFound.set(false);
                                return;
                            }
                            instanceInfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                        } catch (Exception e) { // Response could be : [PANIC]yaml: mapping values are not allowed in this context

                        }
                    });
                    if (nodeFound.get()) {
                        this.notifyInstanceStateChanged(deploymentContext.getDeploymentPaaSId(), nodeInfosRes.getName(),
                                instInfoRes.getId(), intancesInfos.get(nodeInfosRes.getName()).get(instInfoRes.getId()));
                    }
                }
            }
        }
    }

    private void setNodeAttributes(String deploymentUrl, String deploymentPaaSId, final String nodeName, final String instanceName) throws Exception {
            Map<String, Map<String, InstanceInformation>> intancesInfos =  this.runtimeDeploymentInfos.get(deploymentPaaSId).getInstanceInformations();
            DeployInfosResponse deployRes =  this.restClient.getDeploymentInfosFromJanus(deploymentUrl);

            List<Link> nodes = deployRes.getLinks().stream().filter(link -> link.getRel().equals("node") && link.getHref().endsWith(nodeName)).collect(Collectors.toList());
            for(Link nodeLink : nodes) {
                NodeInfosResponse nodeInfosRes = this.restClient.getNodesInfosFromJanus(nodeLink.getHref());
                List<Link> instances = nodeInfosRes.getLinks().stream().filter(link -> link.getRel().equals("instance") && link.getHref().endsWith(instanceName)).collect(Collectors.toList());
                for(Link instanceLink : instances) {
                    if(instanceLink.getRel().equals("instance")) {
                        AtomicBoolean nodeFound = new AtomicBoolean(true);
                        InstanceInfosResponse instInfoRes = this.restClient.getInstanceInfosFromJanus(instanceLink.getHref());
                        instInfoRes.getLinks().stream().filter(attributeLink -> attributeLink.getRel().equals("attribute")).forEach(attributeLink -> {
                            try {
                                AttributeResponse attrRes = this.restClient.getAttributeFromJanus(attributeLink.getHref());
                                log.info("--------------------------------");
                                log.info("{}", attrRes);

                                Map<String, InstanceInformation> nodeInstancesInfos = intancesInfos.get(nodeInfosRes.getName());
                                if (nodeInstancesInfos == null) {
                                    nodeFound.set(false);
                                    return;
                                }
                                InstanceInformation instanceInfo = nodeInstancesInfos.get(instInfoRes.getId());
                                if (nodeInstancesInfos == null) {
                                    nodeFound.set(false);
                                    return;
                                }
                                instanceInfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                            } catch (Exception e) { // Response could be : [PANIC]yaml: mapping values are not allowed in this context

                            }
                        });
                        if (nodeFound.get()) {
                            this.notifyInstanceStateChanged(deploymentPaaSId, nodeInfosRes.getName(), instInfoRes.getId(),
                                    intancesInfos.get(nodeInfosRes.getName()).get(instInfoRes.getId()));
                        }
                    }
                }
            }
        }

    private void listenDeploymentEvent(String deploymentUrl, String deploymentPaaSId) {
        Map<String, Map<String, InstanceInformation>> intancesInfos =  this.runtimeDeploymentInfos.get(deploymentPaaSId).getInstanceInformations();
        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    EventResponse eventResponse = this.restClient.getEventFromJanus(deploymentUrl, prevIndex);
                    if (eventResponse == null) {
                        TimeUnit.SECONDS.sleep(1);
                        continue;
                    }
                    prevIndex = eventResponse.getLast_index();
                    if (eventResponse.getEvents() != null) {
                        for (Event event : eventResponse.getEvents()) {
                            log.info("[listenDeploymentEvent] " + event.getNode() +  " "  +event.getStatus());
                            this.sendMesage(deploymentPaaSId, "[listenDeploymentEvent] " + event.getNode() + " " + event.getStatus());

                            Map<String, InstanceInformation> nodeInstancesInfos = intancesInfos.get(event.getNode());
                            if (nodeInstancesInfos == null) {
                                continue;
                            }

                            String instanceId = event.getInstance(); // Need to change when Janus api change
                            if (event.getStatus().equals("started")) {
                                this.setNodeAttributes(deploymentUrl, deploymentPaaSId, event.getNode(), instanceId);
                            }

                            InstanceInformation infos = nodeInstancesInfos.get(instanceId);
                            infos.setState(event.getStatus());
                            if (event.getStatus().equals("started")) {
                                infos.setInstanceStatus(InstanceStatus.SUCCESS);
                            } else if (event.getStatus().equals("error")) {
                                infos.setInstanceStatus(InstanceStatus.FAILURE);
                            }
                            this.notifyInstanceStateChanged(deploymentPaaSId, event.getNode(), instanceId, infos);

                        }
                    }
                } catch (InterruptedException e) {
                    String threadName = Thread.currentThread().getName();
                    log.info("[listenDeploymentEvent] Stopped " + threadName + " " + deploymentPaaSId);
                    return;
                } catch (JanusRestException e) {
                    if (e.getHttpStatusCode() == 404) {
                        log.info("[listenDeploymentEvent] Stopped got 404 exception " + deploymentPaaSId);
                        return;
                    }
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        this.runtimeDeploymentInfos.get(deploymentPaaSId).getExecutor().submit(task);
    }


    private void addPremiumLog(PaaSDeploymentContext deploymentContext, String log, Date date, PaaSDeploymentLogLevel level) {
        Deployment deployment = deploymentContext.getDeployment();
        PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
        deploymentLog.setDeploymentId(deployment.getId());
        deploymentLog.setDeploymentPaaSId(deployment.getOrchestratorDeploymentId());
        deploymentLog.setContent(log);
        deploymentLog.setLevel(level);
        deploymentLog.setTimestamp(date);
        alienMonitorDao.save(deploymentLog);
    }
    private void listenJanusLog(String deploymentUrl, PaaSDeploymentContext deploymentContext) {
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
                            this.sendMesage(deploymentPaaSId, logEvent.getLogs());
                            addPremiumLog(deploymentContext, logEvent.getLogs(),  logEvent.getDate(),PaaSDeploymentLogLevel.INFO);
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
                    e.printStackTrace();
                }
            }

        };
        this.runtimeDeploymentInfos.get(deploymentPaaSId).getExecutor().submit(task);
    }

    private void checkJanusStatusUntil(String aimStatus, String deploymentUrl) throws Exception {
        String status = "";
        while (!status.equals(aimStatus) && !status.contains("FAILED")) {
            status = restClient.getStatusFromJanus(deploymentUrl);
            log.info(status);
            Thread.sleep(2000);
        }
    }

    @Override
    protected synchronized void doUndeploy(final PaaSDeploymentContext deploymentContext) {
        log.info(this.doGetStatus(deploymentContext.getDeploymentPaaSId(), false).toString());
        if (this.doGetStatus(deploymentContext.getDeploymentPaaSId(), false) == DeploymentStatus.DEPLOYMENT_IN_PROGRESS) {
            return;
        }

        log.info("Undeploying deployment [" + deploymentContext.getDeploymentPaaSId() + "]");
        changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);

        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());
        if (runtimeDeploymentInfo != null) {
            Map<String, Map<String, InstanceInformation>> appInfo = runtimeDeploymentInfo.getInstanceInformations();
            for (Map.Entry<String, Map<String, InstanceInformation>> nodeEntry : appInfo.entrySet()) {
                for (Map.Entry<String, InstanceInformation> instanceEntry : nodeEntry.getValue().entrySet()) {
                    instanceEntry.getValue().setState("stopping");
                    instanceEntry.getValue().setInstanceStatus(InstanceStatus.PROCESSING);
                    notifyInstanceStateChanged(deploymentContext.getDeploymentPaaSId(), nodeEntry.getKey(), instanceEntry.getKey(), instanceEntry.getValue());
                }
            }
        }

        Runnable task = () -> {
            try {
                String deploymentUrl = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getDeploymentUrl();
                restClient.undeployJanus(deploymentUrl);
                checkJanusStatusUntil("UNDEPLOYED", deploymentUrl);
            } catch (Exception e) {
                sendMesage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
                changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
                throw new RuntimeException(e.getMessage()); // TODO : Refactor, For detecting error deploy rest API A4C, when integrationt test
            }

            changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYED);
            // cleanup deployment cache
            runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getExecutor().shutdownNow();
            runtimeDeploymentInfos.remove(deploymentContext.getDeploymentPaaSId());
        };

        Thread thread = new Thread(task);
        thread.start();
    }

    @Override
    protected synchronized DeploymentStatus doChangeStatus(final String deploymentPaaSId, final DeploymentStatus status) {
        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentPaaSId);
        DeploymentStatus oldDeploymentStatus = runtimeDeploymentInfo.getStatus();
        log.info("Deployment [" + deploymentPaaSId + "] moved from status [" + oldDeploymentStatus + "] to [" + status + "]");
        runtimeDeploymentInfo.setStatus(status);


        PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
        event.setDeploymentStatus(status);
        event.setDate((new Date()).getTime());
        event.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        toBeDeliveredEvents.add(event);
        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate((new Date()).getTime());
        messageMonitorEvent.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        messageMonitorEvent.setMessage("APPLICATIONS.RUNTIME.EVENTS.MESSAGE_EVENT.STATUS_DEPLOYMENT_CHANGED");
        toBeDeliveredEvents.add(messageMonitorEvent);


        return oldDeploymentStatus;
    }

    protected synchronized void sendMesage(final String deploymentPaaSId, final String message) {
        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate((new Date()).getTime());
        messageMonitorEvent.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        messageMonitorEvent.setMessage(message);
        toBeDeliveredEvents.add(messageMonitorEvent);
    }

    private void notifyInstanceStateChanged(final String deploymentPaaSId, final String nodeId, final String instanceId, final InstanceInformation information){
        final InstanceInformation cloned = new InstanceInformation();
        cloned.setAttributes(information.getAttributes());
        cloned.setInstanceStatus(information.getInstanceStatus());
        cloned.setRuntimeProperties(information.getRuntimeProperties());
        cloned.setState(information.getState());

        final JanusRuntimeDeploymentInfo deploymentInfo = runtimeDeploymentInfos.get(deploymentPaaSId);
        Deployment deployment = deploymentInfo.getDeploymentContext().getDeployment();
        PaaSInstanceStateMonitorEvent event;
        event = new PaaSInstanceStateMonitorEvent();
        event.setInstanceId(instanceId);
        event.setInstanceState(cloned.getState());
        event.setInstanceStatus(cloned.getInstanceStatus());
        event.setNodeTemplateId(nodeId);
        event.setDate((new Date()).getTime());
        event.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        event.setRuntimeProperties(cloned.getRuntimeProperties());
        event.setAttributes(cloned.getAttributes());
        toBeDeliveredEvents.add(event);

        if (deployment.getSourceName().equals(BLOCKSTORAGE_APPLICATION) && cloned.getState().equalsIgnoreCase("created")) {
            PaaSInstancePersistentResourceMonitorEvent prme = new PaaSInstancePersistentResourceMonitorEvent(nodeId, instanceId,
                    NormativeBlockStorageConstants.VOLUME_ID, UUID.randomUUID().toString());
            toBeDeliveredEvents.add(prme);
        }

        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate((new Date()).getTime());
        messageMonitorEvent.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        messageMonitorEvent.setMessage("APPLICATIONS.RUNTIME.EVENTS.MESSAGE_EVENT.INSTANCE_STATE_CHANGED");
        toBeDeliveredEvents.add(messageMonitorEvent);
    }

    private RelationshipType getRelationshipType(String typeName) {
        return toscaTypeSearchService.findMostRecent(RelationshipType.class, typeName);
    }

    private void doScaledUpNode(ScalingVisitor scalingVisitor, String nodeTemplateId, Map<String, NodeTemplate> nodeTemplates) {
        scalingVisitor.visit(nodeTemplateId);
        for (Entry<String, NodeTemplate> nEntry : nodeTemplates.entrySet()) {
            if (nEntry.getValue().getRelationships() != null) {
                for (Entry<String, RelationshipTemplate> rt : nEntry.getValue().getRelationships().entrySet()) {
                    RelationshipType relType = getRelationshipType(rt.getValue().getType());
                    if (nodeTemplateId.equals(rt.getValue().getTarget()) && ToscaUtils.isFromType(NormativeRelationshipConstants.HOSTED_ON, relType)) {
                        doScaledUpNode(scalingVisitor, nEntry.getKey(), nodeTemplates);
                    }
                }
            }
        }
    }

    @Override
    public void init(Map<String, PaaSTopologyDeploymentContext> activeDeployments) {

    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, final int instances, IPaaSCallback<?> callback) {
        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());

        if (runtimeDeploymentInfo == null) {
            return;
        }

        Topology topology = runtimeDeploymentInfo.getDeploymentContext().getDeploymentTopology();
        final Map<String, Map<String, InstanceInformation>> existingInformations = runtimeDeploymentInfo.getInstanceInformations();
        if (existingInformations != null && existingInformations.containsKey(nodeTemplateId)) {
            ScalingVisitor scalingVisitor = nodeTemplateId1 -> {
                Map<String, InstanceInformation> nodeInformations = existingInformations.get(nodeTemplateId1);
                if (nodeInformations != null) {
                    int currentSize = nodeInformations.size();
                    if (instances > 0) {
                        for (int i = currentSize; i < currentSize + instances; i++) {
                            nodeInformations.put(String.valueOf(i), newInstance(i));
                        }
                    } else {
                        for (int i = currentSize + instances; i < currentSize; i++) {
                            if (nodeInformations.containsKey(String.valueOf(i))) {
                                nodeInformations.get(String.valueOf(i)).setState("stopping");
                                nodeInformations.get(String.valueOf(i)).setInstanceStatus(InstanceStatus.PROCESSING);
                            }
                        }
                    }
                }
            };
            doScaledUpNode(scalingVisitor, nodeTemplateId, topology.getNodeTemplates());
        }
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext deploymentContext, final String workflowName, Map<String, Object> inputs, final IPaaSCallback<?> callback) {
        log.info(String.format("Execution of workflow %s is scheduled", workflowName));
        executorService.schedule(() -> {
            log.info(String.format("Execution of workflow %s is done", workflowName));
            callback.onSuccess(null);
        }, 5L, TimeUnit.SECONDS);
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        DeploymentStatus status = doGetStatus(deploymentContext.getDeploymentPaaSId(), false);
        callback.onSuccess(status);
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
                                        IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {
        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());
        if (runtimeDeploymentInfo != null) {
            callback.onSuccess(runtimeDeploymentInfo.getInstanceInformations());
        }
    }

    @Override
    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventsCallback) {
        AbstractMonitorEvent[] events = toBeDeliveredEvents.toArray(new AbstractMonitorEvent[toBeDeliveredEvents.size()]);
        toBeDeliveredEvents.clear();
        eventsCallback.onSuccess(events);
    }

    @Override
    protected String doExecuteOperation(NodeOperationExecRequest request) {
        List<String> allowedOperation = Arrays.asList("success", "success_param");
        String result = null;
        try {
            log.info("TRIGGERING OPERATION : {}", request.getOperationName());
            Thread.sleep(3000);
            log.info(" COMMAND REQUEST IS: " + JsonUtil.toString(request));
        } catch (JsonProcessingException | InterruptedException e) {
            log.error("OPERATION execution failled!", e);
            log.info("RESULT IS: KO");
            return "KO";
        }
        // only 2 operations in allowedOperation will return OK
        result = allowedOperation.contains(request.getOperationName()) ? "OK" : "KO";
        log.info("RESULT IS : {}", result);
        return result;
    }

    @Override
    public void setConfiguration(ProviderConfig configuration) throws PluginConfigurationException {
        log.info("In the plugin configurator <" + this.getClass().getName() + ">");
        this.providerConfiguration = configuration;
        this.restClient.setProviderConfiguration(this.providerConfiguration);
    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) {
        String deploymentPaaSId = deploymentContext.getDeploymentPaaSId();

        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());

        Topology topology = runtimeDeploymentInfo.getDeploymentContext().getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> nodes = runtimeDeploymentInfo.getInstanceInformations();

        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (Entry<String, Map<String, InstanceInformation>> nodeEntry : nodes.entrySet()) {
            String nodeTemplateId = nodeEntry.getKey();
            Map<String, InstanceInformation> nodeInstances = nodeEntry.getValue();
            if (nodeInstances != null && !nodeInstances.isEmpty()) {
                NodeTemplate nodeTemplate = topology.getNodeTemplates().get(nodeTemplateId);
                NodeType nodeType = toscaTypeSearchService.getRequiredElementInDependencies(NodeType.class, nodeTemplate.getType(),
                        topology.getDependencies());
                if (ToscaUtils.isFromType(NormativeComputeConstants.COMPUTE_TYPE, nodeType)) {
                    for (Entry<String, InstanceInformation> nodeInstanceEntry : nodeInstances.entrySet()) {
                        String instanceId = nodeInstanceEntry.getKey();
                        InstanceInformation instanceInformation = nodeInstanceEntry.getValue();
                        if (instanceInformation != null) {
                            switchInstanceMaintenanceMode(deploymentPaaSId, nodeTemplateId, instanceId, instanceInformation, maintenanceModeOn);
                        }
                    }
                }
            }
        }
    }

    private void switchInstanceMaintenanceMode(String deploymentPaaSId, String nodeTemplateId, String instanceId, InstanceInformation instanceInformation,
                                               boolean maintenanceModeOn) {
        if (maintenanceModeOn && instanceInformation.getInstanceStatus() == InstanceStatus.SUCCESS) {
            log.info(String.format("switching instance MaintenanceMode ON for node <%s>, instance <%s>", nodeTemplateId, instanceId));
            instanceInformation.setInstanceStatus(InstanceStatus.MAINTENANCE);
            instanceInformation.setState("maintenance");
            notifyInstanceStateChanged(deploymentPaaSId, nodeTemplateId, instanceId, instanceInformation);
        } else if (!maintenanceModeOn && instanceInformation.getInstanceStatus() == InstanceStatus.MAINTENANCE) {
            log.info(String.format("switching instance MaintenanceMode OFF for node <%s>, instance <%s>", nodeTemplateId, instanceId));
            instanceInformation.setInstanceStatus(InstanceStatus.SUCCESS);
            instanceInformation.setState("started");
            notifyInstanceStateChanged(deploymentPaaSId, nodeTemplateId, instanceId, instanceInformation);
        }
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeTemplateId, String instanceId, boolean maintenanceModeOn) {
        log.info(String.format("switchInstanceMaintenanceMode order received for node <%s>, instance <%s>, mode <%s>", nodeTemplateId, instanceId,
                maintenanceModeOn));
        JanusRuntimeDeploymentInfo runtimeDeploymentInfo = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId());
        if (runtimeDeploymentInfo == null) {
            return;
        }

        final Map<String, Map<String, InstanceInformation>> existingInformations = runtimeDeploymentInfo.getInstanceInformations();
        if (existingInformations != null && existingInformations.containsKey(nodeTemplateId)
                && existingInformations.get(nodeTemplateId).containsKey(instanceId)) {
            InstanceInformation instanceInformation = existingInformations.get(nodeTemplateId).get(instanceId);
            switchInstanceMaintenanceMode(deploymentContext.getDeploymentPaaSId(), nodeTemplateId, instanceId, instanceInformation, maintenanceModeOn);
        }
    }

    private interface ScalingVisitor {
        void visit(String nodeTemplateId);
    }

}
