/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin;

import alien4cloud.paas.model.*;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.workflow.Workflow;
import org.alien4cloud.tosca.model.workflow.WorkflowStep;
import org.alien4cloud.tosca.model.workflow.activities.CallOperationWorkflowActivity;
import org.alien4cloud.tosca.model.workflow.activities.DelegateWorkflowActivity;
import org.apache.commons.lang.StringUtils;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogEvent;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * EventListener Task
 */
@Slf4j
public class LogListenerTask extends AlienTask {

    // Set this to false to stop pollong events
    private boolean valid = true;


    public LogListenerTask(YorcPaaSProvider prov) {
        super(prov);
    }

    public void stop() {
        valid = false;
    }
    
    private static final String EVENT_HANDLER_REGEXP = "(.*Workflow.+ended without error.*|.*Start processing workflow.*|.*executing operation.*|.*executing delegate operation.*|.*operation succeeded.*|.*delegate operation succeeded.*|.*operation failed.*|.*delegate operation failed.*|.*Error .* happened in workflow .*)";
    private static final ThreadLocal<Pattern> EVENT_HANDLER_PATTERN = ThreadLocal.withInitial(() -> Pattern.compile(EVENT_HANDLER_REGEXP));
    // a deploymentId -> { TaskKey -> taskId } map
    private Map<String, Map<TaskKey, String>> taskIdCache = Maps.newHashMap();
    // a deploymentId -> DeploymentWrapper map (all currently active deployments)
    private Map<String, DeploymentWrapper> registeredDeployments = Maps.newHashMap();

    /**
     * Listen for Yorc Logs
     */
    public void run() {
        int prevIndex = 1;
        while (valid) {
            try {
                log.debug("Get logs from Yorc from index " + prevIndex);
                LogResponse logResponse = restClient.getLogFromYorc(prevIndex);
                if (logResponse != null) {
                    prevIndex = logResponse.getLast_index();
                    if (logResponse.getLogs() != null) {
                        for (LogEvent logEvent : logResponse.getLogs()) {
                            log.debug("Received log from Yorc: " + logEvent.toString());
                            // add Premium Log
                            PaaSDeploymentLog pLog = toPaasDeploymentLog(logEvent);
                            // here handle the event to generate a4c AbstractMonitorEvent
                            // and enrich the log with taskId
                            try {
                                // just to be sure we don't break anything
                                handleEvent(logEvent, pLog);
                            } catch (Exception e) {
                                log.warn("Not able to handle events", e.getMessage());
                            }
                            postLog(pLog, logEvent.getDeploymentId());
                        }
                    }
                }
            } catch (Exception e) {
                if (valid) {
                    log.warn("listen Yorc Logs Failed", e);
                    try {
                        // We will sleep for 2sec in order to limit logs flood if the Yorc server went down
                        Thread.sleep(2000L);
                    } catch (InterruptedException ex) {
                        log.warn("listenDeploymentEvent wait interrupted ({})", ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Post a PaaSDeploymentLog to a4c premium log
     *
     * @param pdlog
     * @param paasId
     */
    private void postLog(PaaSDeploymentLog pdlog, String paasId) {
        // The DeploymentId is overridden by A4C plugin here with UUID
        pdlog.setDeploymentId(orchestrator.getDeploymentId(paasId));
        pdlog.setDeploymentPaaSId(paasId);
        if (pdlog.getDeploymentId() == null) {
            log.warn("Must provide an Id for this log: " + pdlog.toString());
            return;
        }
        orchestrator.saveLog(pdlog);
    }


    private PaaSDeploymentLog toPaasDeploymentLog(final LogEvent pLogEvent) {
        PaaSDeploymentLog deploymentLog = new PaaSDeploymentLog();
        deploymentLog.setDeploymentId(pLogEvent.getDeploymentId());
        deploymentLog.setContent(pLogEvent.getContent());
        deploymentLog.setExecutionId(pLogEvent.getExecutionId());
        deploymentLog.setInstanceId(pLogEvent.getInstanceId());
        deploymentLog.setInterfaceName(pLogEvent.getInterfaceName());
        deploymentLog.setLevel(PaaSDeploymentLogLevel.fromLevel(pLogEvent.getLevel().toLowerCase()));
        deploymentLog.setType(pLogEvent.getType());
        deploymentLog.setNodeId(pLogEvent.getNodeId());
        deploymentLog.setTimestamp(pLogEvent.getDate());
        deploymentLog.setWorkflowId(pLogEvent.getWorkflowId());
        deploymentLog.setOperationName(pLogEvent.getOperationName());
        return deploymentLog;
    }

    /**
     * Handle Yorc events and :
     * <ul>
     *     <li>generate a4c events related to executions, steps and tasks</li>
     *     <li>enrich {@link PaaSDeploymentLog}s to add the taskId (to make logs filterable by taskId)</li>
     * </ul>
     * This is a workaround to wait for execution/tasks refactoring in Yorc.
     *
     * @param pLogEvent
     * @param pLog
     */
    private void handleEvent(final LogEvent pLogEvent, PaaSDeploymentLog pLog) {
        String content = pLogEvent.getContent();
        log.trace("Handling an event with content : {}", pLogEvent.getContent());
        Map<TaskKey, String> taskIds = taskIdCache.get(pLogEvent.getDeploymentId());
        if (taskIds == null) {
            taskIds = Maps.newHashMap();
            taskIdCache.put(pLogEvent.getDeploymentId(), taskIds);
        }

        TaskKey taskKey = null;
        String taskId = null;
        if (StringUtils.isNotEmpty(pLogEvent.getNodeId()) && StringUtils.isNotEmpty(pLogEvent.getInterfaceName()) && StringUtils.isNotEmpty(pLogEvent.getOperationName())) {
            taskKey = new TaskKey(pLogEvent.getNodeId(), pLogEvent.getInstanceId(), pLogEvent.getInterfaceName(), pLogEvent.getOperationName());
            taskId = taskIds.get(taskKey);
        }

        if (content != null && EVENT_HANDLER_PATTERN.get().matcher(content.replaceAll("\\n", "")).matches()) {
            if (content.equals("executing operation") || content.equals("executing delegate operation")) {
                // generate task and wfStepInstance
                if (taskId == null) {
                    taskId = UUID.randomUUID().toString();
                    taskIds.put(taskKey, taskId);
                } else {
                    // it's a new instance
                }
                String stepId = getStepId(pLogEvent);
                if (stepId != null) {
                    WorkflowStepStartedEvent workflowStepStartedEvent = new WorkflowStepStartedEvent();
                    workflowStepStartedEvent.setStepId(stepId);
                    postWorkflowStepEvent(workflowStepStartedEvent, pLogEvent);
                }
                // a task has been sent ...
                TaskSentEvent taskSentEvent = new TaskSentEvent();
                taskSentEvent.setTaskId(taskId);
                taskSentEvent.setWorkflowStepId(stepId);
                postTaskEvent(taskSentEvent, pLogEvent);
                // ... and started
                TaskStartedEvent taskStartedEvent = new TaskStartedEvent();
                taskStartedEvent.setTaskId(taskId);
                taskStartedEvent.setWorkflowStepId(stepId);
                postTaskEvent(taskStartedEvent, pLogEvent);
            } else if (content.startsWith("Start processing workflow")) {
                // -> PaasWorkflowStartedEvent
                PaaSWorkflowStartedEvent wse = new PaaSWorkflowStartedEvent();
                wse.setWorkflowName(pLogEvent.getWorkflowId());
                postWorkflowMonitorEvent(wse, pLogEvent);
            } else if (content.endsWith("ended without error")) {
                // -> PaasWorkflowSucceededEvent
                PaaSWorkflowSucceededEvent wse = new PaaSWorkflowSucceededEvent();
                postWorkflowMonitorEvent(wse, pLogEvent);
                // if this is uninstall and the deployment is marked as undeploying then remove from map
                if (pLogEvent.getWorkflowId().equals("uninstall")) {
                    DeploymentWrapper deploymentWrapper = registeredDeployments.get(pLogEvent.getDeploymentId());
                    if (deploymentWrapper != null && deploymentWrapper.aboutToBeUndeployed) {
                        registeredDeployments.remove(pLogEvent.getDeploymentId());
                    }
                }
            } else if (content.equals("operation succeeded") || content.equals("delegate operation succeeded") ) {
                // -> TaskSucceedeEvent
                TaskSucceededEvent taskSucceededEvent = new TaskSucceededEvent();
                taskSucceededEvent.setTaskId(taskId);
                postTaskEvent(taskSucceededEvent, pLogEvent);
                String stepId = getStepId(pLogEvent);
                if (stepId != null) {
                    WorkflowStepCompletedEvent workflowStepCompletedEvent = new WorkflowStepCompletedEvent();
                    workflowStepCompletedEvent.setStepId(stepId);
                    postWorkflowStepEvent(workflowStepCompletedEvent, pLogEvent);
                }
            } else if (content.equals("operation failed") || content.equals("delegate operation failed")) {
                // -> TaskSucceedeEvent
                TaskFailedEvent taskFailedEvent = new TaskFailedEvent();
                taskFailedEvent.setTaskId(taskId);
                postTaskEvent(taskFailedEvent, pLogEvent);
                String stepId = getStepId(pLogEvent);
                if (stepId != null) {
                    WorkflowStepCompletedEvent workflowStepCompletedEvent = new WorkflowStepCompletedEvent();
                    workflowStepCompletedEvent.setStepId(stepId);
                    postWorkflowStepEvent(workflowStepCompletedEvent, pLogEvent);
                }
            } else if (content.matches(".*Error .* happened in workflow .*")) {
                PaaSWorkflowFailedEvent wse = new PaaSWorkflowFailedEvent();
                postWorkflowMonitorEvent(wse, pLogEvent);
            }
        }
        // eventually enrich the pLog with taskId
        if (taskId != null) {
            // this way this log will be filterable by this field
            pLog.setTaskId(taskId);
        }
    }

    /**
     * For the given stuffs, return the corresponding stepName or null if not found.
     */
    private synchronized String getStepId(LogEvent pLogEvent) {
        if (pLogEvent.getDeploymentId() == null || pLogEvent.getWorkflowId() == null || pLogEvent.getNodeId() == null || pLogEvent.getInterfaceName() == null || pLogEvent.getOperationName() == null) {
            return null;
        }
        DeploymentWrapper deploymentWrapper = registeredDeployments.get(pLogEvent.getDeploymentId());
        if (deploymentWrapper == null) {
            return null;
        }
        Workflow workflow = deploymentWrapper.deploymentTopology.getWorkflows().get(pLogEvent.getWorkflowId());
        if (workflow == null) {
            return null;
        }
        Optional<WorkflowStep> step = workflow.getSteps().values().stream().filter(workflowStep -> {
            if (workflowStep.getTarget().equals(pLogEvent.getNodeId())) {
                if (workflowStep.getActivity() instanceof CallOperationWorkflowActivity) {
                    CallOperationWorkflowActivity activity = (CallOperationWorkflowActivity) workflowStep.getActivity();
                    // FIXME : don't try to match onto interfaceName since it's not the same (configure vs Configure)
                    if (/*activity.getInterfaceName().equals(pLogEvent.getInterfaceName()) &&*/ activity.getOperationName().equals(pLogEvent.getOperationName())) {
                        return true;
                    }
                } else if (pLogEvent.getInterfaceName().equals("delegate") && workflowStep.getActivity() instanceof DelegateWorkflowActivity) {
                    DelegateWorkflowActivity activity = (DelegateWorkflowActivity)workflowStep.getActivity();
                    if (pLogEvent.getOperationName().equals(activity.getDelegate())) {
                        return true;
                    }
                }
            }
            return false;
        }).findFirst();
        if (step.isPresent()) {
            return step.get().getName();
        }
        return null;
    }

    /**
     * We need to know the workflow steps for each deployment in order to guess stepId regarding nodeId, interfaceName and operationName.
     *
     * @param ctx
     */
    public synchronized void registerDeployment(PaaSTopologyDeploymentContext ctx) {
        registeredDeployments.put(ctx.getDeploymentPaaSId(), new DeploymentWrapper(ctx.getDeploymentTopology()));
    }

    public synchronized void unregisterDeployment(PaaSDeploymentContext ctx) {
        // the deployment should be marked as undeployed, this means we will forgot it after uninstall workflow has been terminated with success.
        DeploymentWrapper deploymentWrapper = registeredDeployments.get(ctx.getDeploymentPaaSId());
        if (deploymentWrapper == null) {
            deploymentWrapper = new DeploymentWrapper(ctx.getDeploymentTopology());
            registeredDeployments.put(ctx.getDeploymentPaaSId(), deploymentWrapper);
        }
        deploymentWrapper.aboutToBeUndeployed = true;
    }

    private void postWorkflowStepEvent(AbstractWorkflowStepEvent event, LogEvent pLogEvent) {
        event.setNodeId(pLogEvent.getNodeId());
        event.setInstanceId(pLogEvent.getInstanceId());
        event.setOperationName(pLogEvent.getInterfaceName() + "." + pLogEvent.getOperationName());
        postWorkflowMonitorEvent(event, pLogEvent);
    }

    private void postTaskEvent(AbstractTaskEvent event, LogEvent pLogEvent) {
        event.setNodeId(pLogEvent.getNodeId());
        event.setInstanceId(pLogEvent.getInstanceId());
        event.setOperationName(pLogEvent.getInterfaceName() + "." + pLogEvent.getOperationName());
        postWorkflowMonitorEvent(event, pLogEvent);
    }

    private void postWorkflowMonitorEvent(AbstractPaaSWorkflowMonitorEvent a4cEvent, LogEvent yorcEvent) {
        a4cEvent.setExecutionId(yorcEvent.getExecutionId());
        a4cEvent.setWorkflowId(yorcEvent.getWorkflowId());
        orchestrator.postEvent(a4cEvent, yorcEvent.getDeploymentId());
    }

    private class TaskKey {
        public final String nodeId;
        public final String instanceId;
        public final String interfaceName;
        public final String operationName;

        public TaskKey(String nodeId, String instanceId, String interfaceName, String operationName) {
            this.nodeId = nodeId;
            this.instanceId = instanceId;
            this.interfaceName = interfaceName;
            this.operationName = operationName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaskKey taskId = (TaskKey) o;
            return Objects.equals(nodeId, taskId.nodeId) &&
                    Objects.equals(instanceId, taskId.instanceId) &&
                    Objects.equals(interfaceName, taskId.interfaceName) &&
                    Objects.equals(operationName, taskId.operationName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, instanceId, interfaceName, operationName);
        }
    }

    private class DeploymentWrapper {
        private boolean aboutToBeUndeployed;
        public final Topology deploymentTopology;

        private DeploymentWrapper(Topology deploymentTopology) {
            this.deploymentTopology = deploymentTopology;
        }
    }

}
