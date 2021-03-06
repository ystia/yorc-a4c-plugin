/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin;

import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

import javax.annotation.Resource;
import javax.inject.Inject;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.*;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.parser.ToscaParser;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.index.IToscaTypeSearchService;
import org.alien4cloud.tosca.catalog.repository.CsarFileRepository;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.elasticsearch.common.collect.Maps;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.ystia.yorc.alien4cloud.plugin.location.AbstractLocationConfigurerFactory;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.*;
import org.ystia.yorc.alien4cloud.plugin.rest.RestClient;
import org.ystia.yorc.alien4cloud.plugin.rest.YorcRestException;
import org.ystia.yorc.alien4cloud.plugin.service.PluginArchiveService;
import org.ystia.yorc.alien4cloud.plugin.service.ToscaComponentExporter;
import org.ystia.yorc.alien4cloud.plugin.service.ToscaTopologyExporter;

/**
 * a4c yorc plugin
 * This class is abstract since it extends YstiaOrchestrator
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class YorcPaaSProvider implements IOrchestratorPlugin<ProviderConfig> {

    private final Map<String, YorcRuntimeDeploymentInfo> runtimeDeploymentInfos = Maps.newConcurrentMap();
    private final List<AbstractMonitorEvent> toBeDeliveredEvents = new ArrayList<>();
    private ProviderConfig providerConfiguration;
    private Map<String, String> a4cDeploymentIds = Maps.newHashMap();
    private EventListenerTask eventListenerTask;
    private LogListenerTask logListenerTask;

    /**
     * Keep in mind the paas (yorc) deploymentIds that have no
     * correspondent deploymentId in Alien. This can arrive for multiple reasons:
     * - deployment was created with yorc's CLI
     * - deployment was created with another Alien instance
     * - Alien was restared after runtime removed
     */
    private List<String> unknownDeploymentIds = new ArrayList<>();

    private boolean isUnknownDeploymentId(String paasId) {
        return unknownDeploymentIds.contains(paasId);
    }

    private void addUnknownDeploymentId(String paasId) {
        unknownDeploymentIds.add(paasId);
    }

    @Inject
    private IToscaTypeSearchService toscaTypeSearchService;

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;

    @Inject
    private CsarFileRepository fileRepository;

    private RestClient restClient = new RestClient();

    private TaskManager taskManager;

    @Resource
    private ICSARRepositorySearchService csarRepoSearchService;

    @Resource
    @Getter
    private ToscaTopologyExporter toscaTopologyExporter;

    @Resource
    @Getter
    private ToscaComponentExporter toscaComponentExporter;

    @Resource(name = "yorc-tosca-parser")
    @Getter
    private ToscaParser parser;


    @Inject
    private AbstractLocationConfigurerFactory yorcLocationConfigurerFactory;

    @Inject
    private PluginArchiveService archiveService;

    /**
     * Default constructor
     */
    public YorcPaaSProvider() {
        // Start the TaskManager
        // TODO make sizes configurable
        taskManager = new TaskManager(3, 120, 3600);
        logListenerTask = new LogListenerTask(this);
    }

    public void stopLogsAndEvents() {
        if (eventListenerTask != null) eventListenerTask.stop();
        if (logListenerTask != null) logListenerTask.stop();
        if (taskManager != null) taskManager.stop();
    }

    public void startLogsAndEvents() {
        // Listen Events and logs from yorc about the deployment
        log.info("Starting Yorc events & logs listeners");
        eventListenerTask = new EventListenerTask(this);
        logListenerTask = new LogListenerTask(this);
        addTask(eventListenerTask);
        addTask(logListenerTask);
    }

    public RestClient getRestClient() {
        return restClient;
    }

    /**
     * Add a task in the task manager
     * @param task
     */
    public void addTask(AlienTask task) {
        taskManager.addTask(task);
    }

    public void putDeploymentId(String paasId, String alienId) {
        a4cDeploymentIds.put(paasId, alienId);
    }

    /**
     * Return the alien generated id for this deployment
     * @param paasId the orchestrator's id
     * @return the alien's id
     */
    public String getDeploymentId(String paasId) {
        String  deploymentId = a4cDeploymentIds.get(paasId);
        if (deploymentId == null) {
            // if we don't know yet that this deployment is unknown by Alien
            if (!isUnknownDeploymentId(paasId)) {
                log.warn("The orchestrator deploymentID: {} doesn't match with any associated Alien4Cloud deploymentID.", paasId);
                // cache this information
                addUnknownDeploymentId(paasId);
            }
        }
        return deploymentId;
    }

    public void putDeploymentInfo(String paasId, YorcRuntimeDeploymentInfo jrdi) {
        runtimeDeploymentInfos.put(paasId, jrdi);
    }

    public YorcRuntimeDeploymentInfo getDeploymentInfo(String paasId) {
        return runtimeDeploymentInfos.get(paasId);
    }

    public void removeDeploymentInfo(String paasId) {
        runtimeDeploymentInfos.remove(paasId);
        // TODO Stop threads listening log and events (maybe nothing to do)
    }

    public Path getCSAR(String name, String vers) {
        Path ret = fileRepository.getExpandedCSAR(name, vers);
        return ret;
    }

    public void saveLog(PaaSDeploymentLog pdlog) {
        log.debug(pdlog.toString());
        alienMonitorDao.save(pdlog);
    }

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
    public Set<String> init(Map<String, String> activeDeployments) {
        log.info("Init plugin for " + activeDeployments.size() + " active deployments");

        // Update deployment info for all active deployments
        for (Map.Entry<String, String> entry : activeDeployments.entrySet()) {
            String yorcDeploymentId = entry.getKey();
            String deploymentId = entry.getValue();
            log.info("Active deployment: " + yorcDeploymentId);
            doUpdateDeploymentInfo(yorcDeploymentId, deploymentId);
        }
        // prov
        log.info(fileRepository.getRootPath().toString());
        startLogsAndEvents();
        // Support Alien4Cloud 2.2.0
        return activeDeployments.keySet();
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
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            status = DeploymentStatus.UNDEPLOYED;
        } else {
            status = jrdi.getStatus();
        }
        callback.onSuccess(status);
    }

    /**
     * Update a topology deployment.
     *
     * @param ctx the PaaSTopologyDeploymentContext of the deployment
     * @param callback to call when update is done or has failed.
     */
    public void update(PaaSTopologyDeploymentContext ctx, IPaaSCallback<?> callback) {
        addTask(new UpdateTask(ctx, this, callback, csarRepoSearchService));
    }

    /**
     * Deploy a topology
     *
     * @param ctx the PaaSTopologyDeploymentContext of the deployment
     * @param callback to call when deployment is done or has failed.
     */
    @Override
    public void deploy(PaaSTopologyDeploymentContext ctx, IPaaSCallback<?> callback) {
        addTask(new DeployTask(ctx, this, callback, csarRepoSearchService));
    }

    /**
     * Undeploy a given topology.
     * @param ctx the context of the un-deployment
     * @param callback
     */
    @Override
    public void undeploy(PaaSDeploymentContext ctx, IPaaSCallback<?> callback) {
        addTask(new UndeployTask(ctx, this, callback));
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
        addTask(new ScaleTask(ctx, this, node, nbi, callback));
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
    public void launchWorkflow(PaaSDeploymentContext ctx, String workflowName, Map<String, Object> inputs, IPaaSCallback<String> callback) {
        addTask(new WorkflowTask(ctx, this, workflowName, inputs, callback));
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
        addTask(new OperationTask(ctx, this, request, callback));
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
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
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
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        log.debug(paasId + " switchMaintenanceMode");
        if (jrdi == null) {
            log.error(paasId + " switchMaintenanceMode: No Deployment Information");
            throw new MaintenanceModeException("No Deployment Information");
        }

        //Topology topology = jrdi.getDeploymentContext().getDeploymentTopology();
        Map<String, Map<String, InstanceInformation>> nodes = jrdi.getInstanceInformations();
        if (nodes == null || nodes.isEmpty()) {
            log.error(paasId + " switchMaintenanceMode: No Node found");
            throw new MaintenanceModeException("No Node found");
        }
        for (Entry<String, Map<String, InstanceInformation>> nodeEntry : nodes.entrySet()) {
            String node = nodeEntry.getKey();
            Map<String, InstanceInformation> nodeInstances = nodeEntry.getValue();
            if (nodeInstances != null && !nodeInstances.isEmpty()) {
                log.warn("SwithchMaintenanceMode is a Not yet supported feature with Alien 2.2.0 for Node: ", node);
                // Support Alien4Cloud 2.2.0
                // TODO
                //NodeTemplate nodeTemplate = topology.getNodeTemplates().get(node);
                //NodeType nodeType = toscaTypeSearchService.getRequiredElementInDependencies(NodeType.class, nodeTemplate.getType(),
                //        topology.getDependencies());
                // ALIEN 2.0.0 Update
                /*
                if (isFromType(NormativeComputeConstants.COMPUTE_TYPE, nodeType)) {
                    for (Entry<String, InstanceInformation> nodeInstanceEntry : nodeInstances.entrySet()) {
                        String instance = nodeInstanceEntry.getKey();
                        InstanceInformation iinfo = nodeInstanceEntry.getValue();
                        if (iinfo != null) {
                            doSwitchInstanceMaintenanceMode(paasId, node, instance, iinfo, maintenanceModeOn);
                        }
                    }
                }*/
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
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
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
    public void setConfiguration(String orchestratorId, ProviderConfig configuration) throws PluginConfigurationException {
        log.info("set config for YorcPaaSProvider");
        providerConfiguration = configuration;
        restClient.setProviderConfiguration(providerConfiguration);
    }

    /**
     * Change status of the deployment in YorcRuntimeDeploymentInfo
     * @param paasId
     * @param status
     */
    public void changeStatus(final String paasId, final DeploymentStatus status) {
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("YorcRuntimeDeploymentInfo is null. paasId=" + paasId);
            return;
        }
        synchronized (jrdi) {
            doChangeStatus(paasId, status);
        }
    }

    /**
     * Actually change the status of the deployment in YorcRuntimeDeploymentInfo
     * Must be called with lock on jrdi
     * @param paasId
     * @param status
     */
    public void doChangeStatus(String paasId, DeploymentStatus status) {
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(paasId);
        if (jrdi == null) {
            log.error("YorcRuntimeDeploymentInfo is null for paasId " + paasId);
            return;
        }

        DeploymentStatus oldDeploymentStatus = jrdi.getStatus();
        log.debug("Deployment [" + paasId + "] moved from status [" + oldDeploymentStatus + "] to [" + status + "]");
        jrdi.setStatus(status);

        PaaSDeploymentStatusMonitorEvent event = new PaaSDeploymentStatusMonitorEvent();
        event.setDeploymentStatus(status);
        postEvent(event, paasId);
    }

    // ------------------------------------------------------------------------------------------------------
    // private methods
    // ------------------------------------------------------------------------------------------------------

    protected void postWorkflowStepEvent(AbstractWorkflowStepEvent event, Event yorcEvent) {
        event.setInstanceId(yorcEvent.getInstanceId());
        event.setNodeId(yorcEvent.getNodeId());
        event.setOperationName(yorcEvent.getOperationName());
        event.setStepId(yorcEvent.getStepId());
        event.setTargetInstanceId(yorcEvent.getTargetInstanceId());
        event.setTargetNodeId(yorcEvent.getTargetNodeId());
        event.setDate(event.getDate());
        event.setExecutionId(yorcEvent.getAlienExecutionId());
        event.setWorkflowId(yorcEvent.getWorkflowId());
        postWorkflowMonitorEvent(event, yorcEvent);
    }

    protected void postTaskEvent(AbstractTaskEvent event, Event yorcEvent) {
        event.setTaskId(yorcEvent.getAlienTaskId());
        event.setInstanceId(yorcEvent.getInstanceId());
        event.setNodeId(yorcEvent.getNodeId());
        event.setOperationName(yorcEvent.getOperationName());
        event.setWorkflowStepId(yorcEvent.getStepId());
        event.setTargetInstanceId(yorcEvent.getTargetInstanceId());
        event.setTargetNodeId(yorcEvent.getTargetNodeId());
        event.setDate(event.getDate());
        event.setExecutionId(yorcEvent.getAlienExecutionId());
        event.setWorkflowId(yorcEvent.getWorkflowId());
        postWorkflowMonitorEvent(event, yorcEvent);
    }

    protected void postWorkflowMonitorEvent(AbstractPaaSWorkflowMonitorEvent a4cEvent, Event yorcEvent) {
        a4cEvent.setDeploymentId(a4cDeploymentIds.get(yorcEvent.getDeploymentId()));
        a4cEvent.setOrchestratorId(yorcEvent.getDeploymentId());
        a4cEvent.setDate(yorcEvent.getDate().getTime());
        a4cEvent.setExecutionId(yorcEvent.getAlienExecutionId());
        a4cEvent.setWorkflowId(yorcEvent.getWorkflowId());
        if (a4cEvent instanceof PaaSWorkflowStartedEvent) {
            PaaSWorkflowStartedEvent wse = (PaaSWorkflowStartedEvent) a4cEvent;
            wse.setWorkflowName(yorcEvent.getWorkflowId());
            postEvent(wse, yorcEvent.getDeploymentId());
            return;
        }

        postEvent(a4cEvent, yorcEvent.getDeploymentId());
    }

    /**
     * Update Instance State and notify alien4cloud if needed
     * @param paasId Deployment PaaS Id
     * @param nodeId
     * @param instanceId
     * @param iinfo
     * @param state
     */
    public void updateInstanceState(String paasId, String nodeId, String instanceId, InstanceInformation iinfo, String state) {
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
     * Update Deployment Info from Yorc information
     * Called at init, for each active deployment.
     * @param yorcDeploymentId
     * @param deploymentId
     */
    protected void doUpdateDeploymentInfo(String yorcDeploymentId, String deploymentId) {
        String paasId = yorcDeploymentId;
        a4cDeploymentIds.put(paasId, deploymentId);
        String deploymentUrl = "/deployments/" + paasId;
        log.debug("update deployment info " + paasId);

        // Create the YorcRuntimeDeploymentInfo for this deployment
        Map<String, Map<String, InstanceInformation>> nodemap = Maps.newHashMap();
        YorcRuntimeDeploymentInfo jrdi = new YorcRuntimeDeploymentInfo(DeploymentStatus.UNKNOWN, nodemap, deploymentUrl);
        runtimeDeploymentInfos.put(paasId, jrdi);

        DeploymentStatus ds = null;
        try {
            ds = updateNodeInfo(yorcDeploymentId);
        } catch (Exception e) {
            log.error(paasId + " : Cannot update DeploymentInfo ", e);
        }
    }

    /**
     * Update nodeInformation in the YorcRuntimeDeploymentInfo
     * This is needed to let a4c know all about the nodes and their instances
     * Information is got from Yorc using the REST API
     *
     * @param yorcDeploymentId
     * @return deployment status
     *
     * @throws
     */
    private DeploymentStatus updateNodeInfo(String yorcDeploymentId) throws Exception {
        String deploymentUrl = "/deployments/" + yorcDeploymentId;
        log.debug("updateNodeInfo " + yorcDeploymentId);

        // Assumes YorcRuntimeDeploymentInfo already created.
        YorcRuntimeDeploymentInfo jrdi = runtimeDeploymentInfos.get(yorcDeploymentId);
        if (jrdi == null) {
            log.error("No YorcRuntimeDeploymentInfo");
            return DeploymentStatus.FAILURE;
        }

        // Find the deployment info from Yorc
        DeployInfosResponse deployRes = restClient.getDeploymentInfosFromYorc(deploymentUrl);
        DeploymentStatus ds = getDeploymentStatusFromString(deployRes.getStatus());
        jrdi.setStatus(ds);

        Map<String, Map<String, InstanceInformation>> nodemap = jrdi.getInstanceInformations();

        // Look every node we want to update.
        for (Link nodeLink : deployRes.getLinks()) {
            if (nodeLink.getRel().equals("node")) {

                // Find the node info from Yorc
                NodeInfosResponse nodeRes = restClient.getNodesInfosFromYorc(nodeLink.getHref());
                String node = nodeRes.getName();

                Map<String, InstanceInformation> instanceMap = nodemap.get(node);
                if (instanceMap == null) {
                    // This node was unknown. Create it.
                    instanceMap = Maps.newHashMap();
                    nodemap.put(node, instanceMap);
                }
                // Find information about all the node instances from Yorc
                for (Link instanceLink : nodeRes.getLinks()) {
                    if (instanceLink.getRel().equals("instance")) {

                        // Find the instance info from Yorc
                        InstanceInfosResponse instRes = restClient.getInstanceInfosFromYorc(instanceLink.getHref());

                        String inb = instRes.getId();
                        InstanceInformation iinfo = instanceMap.get(inb);
                        if (iinfo == null) {
                            // This instance was unknown. create it.
                            iinfo = newInstance(new Integer(inb));
                            instanceMap.put(inb, iinfo);
                        }
                        for (Link link : instRes.getLinks()) {
                            if (link.getRel().equals("attribute")) {
                                // Get the attribute from Yorc
                                AttributeResponse attrRes = restClient.getAttributeFromYorc(link.getHref());
                                iinfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                                log.debug("Attribute: " + attrRes.getName() + "=" + attrRes.getValue());
                            }
                        }
                        // Let a4c know the instance state
                        updateInstanceState(yorcDeploymentId, node, inb, iinfo, instRes.getStatus());
                    }
                }
            }
        }
        return ds;
    }

    /**
     * Ask Yorc the values of all attributes for this node/instance
     * This method should never throw Exception.
     * @param paasId
     * @param iinfo
     * @param node
     * @param instance
     */
    public void updateInstanceAttributes(String paasId, InstanceInformation iinfo, String node, String instance) {
        String url = "/deployments/" + paasId + "/nodes/" + node + "/instances/" + instance;
        InstanceInfosResponse instInfoRes;
        try {
            instInfoRes = restClient.getInstanceInfosFromYorc(url);
        } catch (Exception e) {
            log.error("Could not get instance info: ", e);
            sendMessage(paasId, "Could not get instance info: " + e.getMessage());
            return;
        }
        for (Link link : instInfoRes.getLinks()) {
            if (link.getRel().equals("attribute")) {
                try {
                    // Get the attribute from Yorc
                    AttributeResponse attrRes = restClient.getAttributeFromYorc(link.getHref());
                    iinfo.getAttributes().put(attrRes.getName(), attrRes.getValue());
                    log.debug("Attribute: " + attrRes.getName() + "=" + attrRes.getValue());
                } catch (YorcRestException jre){
                    // attribute can potentially be not found according to the node state
                    if (jre.getHttpStatusCode() != 404){
                       log.error("Error getting instance attribute " + link.getHref(), jre);
                       sendMessage(paasId, "Error getting instance attribute " + link.getHref());
                    }
                }
                catch (Exception e) {
                    log.error("Error getting instance attribute " + link.getHref(), e);
                    sendMessage(paasId, "Error getting instance attribute " + link.getHref());
                }
            }
        }
    }

    /**
     * Switch Instance Maintenance Mode for this instance
     * TODO This is inefficient: No call to Yorc !
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

    public RelationshipType getRelationshipType(String typeName) {
        return toscaTypeSearchService.findMostRecent(RelationshipType.class, typeName);
    }

    public InstanceInformation newInstance(int i) {
        Map<String, String> attributes = Maps.newHashMap();
        Map<String, String> runtimeProperties = Maps.newHashMap();
        Map<String, String> outputs = Maps.newHashMap();
        return new InstanceInformation(ToscaNodeLifecycleConstants.INITIAL, InstanceStatus.PROCESSING, attributes, runtimeProperties, outputs);
    }

    /**
     * return Instance Status from the instance state
     * See yorc/tosca/states.go (_NodeState_name) but other states may exist for custom commands
     * @param state
     * @return
     */
    private static InstanceStatus getInstanceStatusFromState(String state) {
        switch (state) {
            case "started":
            case "published":
            case "finished":
            case "done":
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
        if (a4cDeploymentIds.get(paasId) == null) {
            log.warn("The orchestrator deploymentID:{} doesn't match with any associated Alien4cloud deploymentID.", paasId);
            return;
        }
        // Set date only if not filled before
        if (event.getDate() == 0) {
            event.setDate((new Date()).getTime());
        }

        event.setDeploymentId(a4cDeploymentIds.get(paasId));
        event.setOrchestratorId(paasId);
        if (event.getDeploymentId() == null) {
            log.warn("Must provide an Id for this Event: " + event.toString());
            return;
        }
        synchronized (toBeDeliveredEvents) {
            toBeDeliveredEvents.add(event);
        }
    }

    /**
     * Maps Yorc DeploymentStatus in alien4cloud DeploymentStatus.
     * See yorc/deployments/structs.go to see all possible values
     * @param state
     * @return
     */
    protected static DeploymentStatus getDeploymentStatusFromString(String state) {
        DeploymentStatus deploymentStatus;
        switch (state) {
            case "DEPLOYED":
                deploymentStatus = DeploymentStatus.DEPLOYED;
                break;
            case "UNDEPLOYED":
            case "PURGED":
                deploymentStatus = DeploymentStatus.UNDEPLOYED;
                break;
            case "DEPLOYMENT_IN_PROGRESS":
            case "SCALING_IN_PROGRESS":
                deploymentStatus = DeploymentStatus.DEPLOYMENT_IN_PROGRESS;
                break;
            case "UNDEPLOYMENT_IN_PROGRESS":
                deploymentStatus = DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS;
                break;
            case "INITIAL":
                deploymentStatus = DeploymentStatus.INIT_DEPLOYMENT;
                break;
            case "DEPLOYMENT_FAILED":
            case "UNDEPLOYMENT_FAILED":
                deploymentStatus = DeploymentStatus.FAILURE;
                break;
            case "UPDATE_IN_PROGRESS":
                deploymentStatus = DeploymentStatus.UPDATE_IN_PROGRESS;
                break;
            case "UPDATED":
                deploymentStatus = DeploymentStatus.UPDATED;
                break;
            case "UPDATE_FAILURE":
                deploymentStatus = DeploymentStatus.UPDATE_FAILURE;
                break;
            default:
                deploymentStatus =  DeploymentStatus.UNKNOWN;
        }

        return deploymentStatus;
    }


    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return yorcLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        List<PluginArchive> archives = Lists.newArrayList();
        archives.add(archiveService.parsePluginArchives("commons/resources"));
        return archives;
    }

}
