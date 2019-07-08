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

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.*;
import lombok.extern.slf4j.Slf4j;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;

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
    private final int WAIT_EVENT_TIMEOUT = 1000 * 60; // 60 seconds
    
    public WorkflowTask(PaaSDeploymentContext ctx, YorcPaaSProvider prov, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
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
        YorcRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
        log.info(paasId + " Execute workflow " + workflowName);

        String taskUrl;
        try {
            taskUrl = restClient.postWorkflowToYorc(deploymentUrl, workflowName, inputs);
        } catch (Exception e) {
            orchestrator.sendMessage(paasId, "Workflow not accepted by Yorc: " + e.getMessage());
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        synchronized (jrdi) {
            // In case we want to undeploy during the workflow.
            jrdi.setDeployTaskId(taskId);
        }
        orchestrator.sendMessage(paasId, "Workflow " + workflowName + " sent to Yorc. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        while (!done && error == null) {
            synchronized (jrdi) {
                // Wait for Events from Yorc
                log.debug(paasId + ": Waiting for Worflow events.");
                try {
                    jrdi.wait(WAIT_EVENT_TIMEOUT);
                } catch (InterruptedException e) {
                    log.error(paasId + ": Interrupted while waiting for Workflow event");
                    break;
                }

                String status;
                // Woke up on wait timeout or event received
                // Checking if this is a workflow event
                Event evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_WORKFLOW) && taskId.equals(evt.getAlienExecutionId())) {
                    jrdi.setLastEvent(null);
                    status = evt.getStatus();
                } else {
                    // No event received for this workflow, checking the task
                    // status to confirm it is ongoing
                    try {
                        status = restClient.getStatusFromYorc(taskUrl).toLowerCase();
                        log.debug("Task " + taskId + " returned status:" + status);
                    } catch (Exception e) {
                        log.debug("Attempting to get task " + taskId + " status throws " + e.getMessage());
                        status = "failed";
                    }
                }
                switch (status) {
                    case "failed":
                        log.debug("Workflow failed");
                        error = new Exception("Workflow failed");
                        break;
                    case "canceled":
                        log.debug("Workflow canceled");
                        error = new Exception("Workflow canceled");
                        break;
                    case "done":
                        log.debug("Workflow OK");
                        done = true;
                        break;
                    default:
                        log.debug("Workflow Status is currently " + status);
                        break;
                }
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
