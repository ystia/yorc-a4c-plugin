/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.IndexedRelationshipType;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.*;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.*;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.plugin.Janus.baseplugin.AbstractPaaSProvider;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.Response.EventResponse;
import alien4cloud.plugin.Janus.rest.Response.LogEvent;
import alien4cloud.plugin.Janus.rest.Response.LogResponse;
import alien4cloud.plugin.Janus.rest.RestClient;
import alien4cloud.plugin.Janus.utils.MappingTosca;
import alien4cloud.plugin.Janus.utils.ShowTopology;
import alien4cloud.plugin.Janus.utils.ZipTopology;
import alien4cloud.plugin.Janus.workflow.WorkflowPlayer;
import alien4cloud.plugin.Janus.workflow.WorkflowReader;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.topology.TopologyService;
import alien4cloud.topology.TopologyUtils;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.NormativeRelationshipConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.collect.Maps;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class JanusPaaSProvider extends AbstractPaaSProvider {
    public static final String PUBLIC_IP = "ip_address";
    public static final String TOSCA_ID = "tosca_id";
    public static final String TOSCA_NAME = "tosca_name";

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private ProviderConfig providerConfiguration;

    private final Map<String, JanusRuntimeDeploymentInfo> runtimeDeploymentInfos = Maps.newConcurrentMap();

    private Map<String, String> paaSDeploymentIdToAlienDeploymentIdMap = Maps.newHashMap();

    private final List<AbstractMonitorEvent> toBeDeliveredEvents = Collections.synchronizedList(new ArrayList<AbstractMonitorEvent>());

    @Resource
    private ICSARRepositorySearchService csarRepoSearchService;

    private static final String BAD_APPLICATION_THAT_NEVER_WORKS = "BAD-APPLICATION";

    private static final String WARN_APPLICATION_THAT_NEVER_WORKS = "WARN-APPLICATION";

    private static final String BLOCKSTORAGE_APPLICATION = "BLOCKSTORAGE-APPLICATION";

    private ShowTopology showTopology = new ShowTopology();

    private WorkflowReader workflowReader;

    private WorkflowPlayer workflowPlayer = new WorkflowPlayer();

    private ZipTopology zipTopology = new ZipTopology();

    private RestClient restClient = RestClient.getInstance();

    @Resource
    private TopologyService topologyService = new TopologyService();

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
        attributes.put(PUBLIC_IP, "10.52.0." + i);
        attributes.put(TOSCA_ID, "1.0-wd03");
        attributes.put(TOSCA_NAME, "TOSCA-Simple-Profile-YAML");
        Map<String, String> runtimeProperties = Maps.newHashMap();
        runtimeProperties.put(PUBLIC_IP, "10.52.0." + i);
        Map<String, String> outputs = Maps.newHashMap();
        return new InstanceInformation(ToscaNodeLifecycleConstants.INITIAL, InstanceStatus.PROCESSING, attributes, runtimeProperties, outputs);
    }

    private ScalingPolicy getScalingPolicy(String id, Map<String, NodeTemplate> nodeTemplates) {
        // Get the scaling of parent if not exist
        Capability scalableCapability = TopologyUtils.getScalableCapability(nodeTemplates, id, false);
        if (scalableCapability == null) {
            if (nodeTemplates.get(id).getRelationships() != null) {
                for (RelationshipTemplate rel : nodeTemplates.get(id).getRelationships().values()) {
                    IndexedRelationshipType relType = getRelationshipType(rel.getType());
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
            for (int i = 1; i <= initialInstances; i++) {
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


        JanusRuntimeDeploymentInfo janusDeploymentInfo = new JanusRuntimeDeploymentInfo(deploymentContext, DeploymentStatus.DEPLOYMENT_IN_PROGRESS, currentInformations, "");
        runtimeDeploymentInfos.put(deploymentContext.getDeploymentPaaSId(), janusDeploymentInfo);


        doChangeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.DEPLOYMENT_IN_PROGRESS);

        MappingTosca.addPreConfigureSteps(topology, deploymentContext.getPaaSTopology());

        //Create the yml of our topology (after substitution)
        String yaml = topologyService.getYaml(topology);
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

        //post topology zip to Janus
        log.info("POST Topology");


        String deploymentUrl;
        try {
            deploymentUrl = restClient.postTopologyToJanus();
        } catch (Exception e) {
            e.printStackTrace();
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

                doChangeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.DEPLOYED);

            } catch (Exception e) {
                e.printStackTrace();
                doChangeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
                sendMesage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
                doChangeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYED);
                runtimeDeploymentInfos.remove(deploymentContext.getDeploymentPaaSId());

                throw new RuntimeException(e.getMessage()); // TODO : Refactor, For detecting error deploy rest API A4C, when integrationt test
            }
        };

        Thread thread = new Thread(task);
        thread.start();

        this.listenDeploymentEvent(deploymentUrl, deploymentContext.getDeploymentPaaSId());
        this.listenJanusLog(deploymentUrl, deploymentContext.getDeploymentPaaSId());
    }

    private void listenDeploymentEvent(String deploymentUrl, String deploymentPaaSId) {
        Map<String, Map<String, InstanceInformation>> intancesInfos =  this.runtimeDeploymentInfos.get(deploymentPaaSId).getInstanceInformations();
        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);

                    EventResponse eventResponse = this.restClient.getEventFromJanus(deploymentUrl, prevIndex);
                    if (eventResponse == null || eventResponse.getEvents() == null || eventResponse.getEvents().isEmpty()) {
                        continue;
                    }
                    prevIndex = eventResponse.getLast_index();
                    for (Event event : eventResponse.getEvents()) {
                        this.sendMesage(deploymentPaaSId, "[listenDeploymentEvent] " + event.getNode() +  " "  +event.getStatus());
                        System.out.println("[listenDeploymentEvent] " + event.getNode() +  " "  +event.getStatus());

                        String instanceId = "1"; // Need to change when Janus api change
                        InstanceInformation infos = intancesInfos.get(event.getNode()).get(instanceId);
                        infos.setState(event.getStatus());
                        if(event.getStatus().equals("started")) { infos.setInstanceStatus(InstanceStatus.SUCCESS); }
                        this.notifyInstanceStateChanged(deploymentPaaSId, event.getNode(), instanceId, infos);
                    }
                } catch (InterruptedException e) {
                    String threadName = Thread.currentThread().getName();
                    System.out.println("[listenDeploymentEvent] Stopped " + threadName + " " + deploymentPaaSId);
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        this.runtimeDeploymentInfos.get(deploymentPaaSId).getExecutor().submit(task);
    }

    private void listenJanusLog(String deploymentUrl, String deploymentPaaSId) {
        Runnable task = () -> {
            int prevIndex = 1;
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);

                    LogResponse logResponse = this.restClient.getLogFromJanus(deploymentUrl, prevIndex);
                    if (logResponse == null || logResponse.getLogs() == null || logResponse.getLogs().isEmpty()) {
                        continue;
                    }
                    prevIndex = logResponse.getLast_index();
                    for (LogEvent logEvent : logResponse.getLogs()) {
                        this.sendMesage(deploymentPaaSId, logEvent.getLogs());
                    }
                } catch (InterruptedException e) {
                    String threadName = Thread.currentThread().getName();
                    System.out.println("[listenJanusLog] Stopped " + threadName + " " + deploymentPaaSId);
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        this.runtimeDeploymentInfos.get(deploymentPaaSId).getExecutor().submit(task);
    }

    private void checkJanusStatusUntil(String aimStatus, String deploymentUrl) throws Exception {
        String status = "";
        while (!status.equals(aimStatus) && !status.equals("DEPLOYMENT_FAILED")) {
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

        try {
            String deploymentUrl = runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getDeploymentUrl();
            restClient.undeployJanus(deploymentUrl);
            checkJanusStatusUntil("UNDEPLOYED", deploymentUrl);
        } catch (Exception e) {
            sendMesage(deploymentContext.getDeploymentPaaSId(), e.getMessage());
            changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.FAILURE);
            throw new RuntimeException(e.getMessage()); // TODO : Refactor, For detecting error deploy rest API A4C, when integrationt test
        }

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

        changeStatus(deploymentContext.getDeploymentPaaSId(), DeploymentStatus.UNDEPLOYED);
        // cleanup deployment cache
        runtimeDeploymentInfos.get(deploymentContext.getDeploymentPaaSId()).getExecutor().shutdownNow();
        runtimeDeploymentInfos.remove(deploymentContext.getDeploymentPaaSId());
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
        event.setInstanceId(instanceId.toString());
        event.setInstanceState(cloned.getState());
        event.setInstanceStatus(cloned.getInstanceStatus());
        event.setNodeTemplateId(nodeId);
        event.setDate((new Date()).getTime());
        event.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        event.setRuntimeProperties(cloned.getRuntimeProperties());
        event.setAttributes(cloned.getAttributes());
        toBeDeliveredEvents.add(event);

        if (deployment.getSourceName().equals(BLOCKSTORAGE_APPLICATION) && cloned.getState().equalsIgnoreCase("created")) {
            PaaSInstancePersistentResourceMonitorEvent prme = new PaaSInstancePersistentResourceMonitorEvent(nodeId, instanceId.toString(),
                    NormativeBlockStorageConstants.VOLUME_ID, UUID.randomUUID().toString());
            toBeDeliveredEvents.add(prme);
        }

        PaaSMessageMonitorEvent messageMonitorEvent = new PaaSMessageMonitorEvent();
        messageMonitorEvent.setDate((new Date()).getTime());
        messageMonitorEvent.setDeploymentId(paaSDeploymentIdToAlienDeploymentIdMap.get(deploymentPaaSId));
        messageMonitorEvent.setMessage("APPLICATIONS.RUNTIME.EVENTS.MESSAGE_EVENT.INSTANCE_STATE_CHANGED");
        toBeDeliveredEvents.add(messageMonitorEvent);
    }

    private interface ScalingVisitor {
        void visit(String nodeTemplateId);
    }

    private IndexedRelationshipType getRelationshipType(String typeName) {
        Map<String, String[]> filters = Maps.newHashMap();
        filters.put("elementId", new String[]{typeName});
        return (IndexedRelationshipType) csarRepoSearchService.search(IndexedRelationshipType.class, null, 0, 1, filters, false).getData()[0];
    }

    private void doScaledUpNode(ScalingVisitor scalingVisitor, String nodeTemplateId, Map<String, NodeTemplate> nodeTemplates) {
        scalingVisitor.visit(nodeTemplateId);
        for (Entry<String, NodeTemplate> nEntry : nodeTemplates.entrySet()) {
            if (nEntry.getValue().getRelationships() != null) {
                for (Entry<String, RelationshipTemplate> rt : nEntry.getValue().getRelationships().entrySet()) {
                    IndexedRelationshipType relType = getRelationshipType(rt.getValue().getType());
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
                        for (int i = currentSize + 1; i < currentSize + instances + 1; i++) {
                            nodeInformations.put(String.valueOf(i), newInstance(i));
                        }
                    } else {
                        for (int i = currentSize + instances + 1; i < currentSize + 1; i++) {
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
        try {
            log.info("The config object Tags is : {}", JsonUtil.toString(configuration.getTags()));
            this.providerConfiguration = configuration;
            this.restClient.setProviderConfiguration(this.providerConfiguration);
        } catch (JsonProcessingException e) {
            log.error("Fails to serialize configuration object as json string", e);
        }
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
                IndexedNodeType nodeType = csarRepoSearchService.getRequiredElementInDependencies(IndexedNodeType.class, nodeTemplate.getType(),
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

}
