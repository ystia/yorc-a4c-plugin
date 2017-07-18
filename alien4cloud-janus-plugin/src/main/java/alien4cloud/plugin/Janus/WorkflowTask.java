/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.RestClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Workflow Task
 */
@Slf4j
public class WorkflowTask extends AlienTask {
    // Needed Info
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;
    String workflowName;
    Map<String, Object> inputs;

    private RestClient restClient = RestClient.getInstance();

    private final int JANUS_OPE_TIMEOUT = 1000 * 3600 * 4;  // 4 hours
    
    public WorkflowTask(PaaSDeploymentContext ctx, JanusPaaSProvider prov, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        super(prov);
        this.ctx = ctx;
        this.workflowName = workflowName;
        this.inputs = inputs;
        this.callback = callback;
    }

    public void run() {
        Throwable error = null;

        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        JanusRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
        log.info(paasId + " Execute workflow " + workflowName);

        String taskUrl;
        try {
            taskUrl = restClient.postWorkflowToJanus(deploymentUrl, workflowName, inputs);
        } catch (Exception e) {
            orchestrator.sendMessage(paasId, "Workflow not accepted by Janus: " + e.getMessage());
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        synchronized (jrdi) {
            // In case we want to undeploy during the workflow.
            jrdi.setDeployTaskId(taskId);
        }
        orchestrator.sendMessage(paasId, "Workflow " + workflowName + " sent to Janus. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        long timeout = System.currentTimeMillis() + JANUS_OPE_TIMEOUT;
        Event evt;
        while (!done && error == null) {
            synchronized (jrdi) {
                // Check for timeout
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    error = new Throwable("Workflow timeout");
                    break;
                }
                // Wait Events from Janus
                log.debug(paasId + ": Waiting for workflow events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for task end");
                    break;
                }
                // Check if we received a Workflow Event and process it
                evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_WORKFLOW) && evt.getTask_id().equals(taskId)) {
                    jrdi.setLastEvent(null);
                    switch (evt.getStatus()) {
                        case "failed":
                            log.warn("Workflow failed: " + paasId);
                            //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                            error = new Exception("Workflow " + workflowName + " failed");
                            break;
                        case "canceled":
                            log.warn("Workflow canceled: " + paasId);
                            //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
                            error = new Exception("Workflow " + workflowName + " canceled");
                            break;
                        case "done":
                            log.debug("Workflow success: " + paasId);
                            //workflowStep(paasId, workflowName, "TODO", "TODO", "TODO");
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
                            log.warn("An event has been ignored. Unexpected status=" + evt.getStatus());
                            break;
                    }
                    continue;
                }
            }
            // We were awaken for some bad reason or a timeout
            // Check Task Status to decide what to do now.
            String status;
            try {
                status = restClient.getStatusFromJanus(taskUrl);
                log.debug("Returned status:" + status);
            } catch (Exception e) {
                status = "FAILED";
            }
            switch (status) {
                case "DONE":
                    // Task OK.
                    log.debug("Workflow OK");
                    done = true;
                    break;
                case "FAILED":
                    log.debug("Workflow failed");
                    error = new Exception("Workflow failed");
                    break;
                default:
                    log.debug("Workflow Status is currently " + status);
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
}
