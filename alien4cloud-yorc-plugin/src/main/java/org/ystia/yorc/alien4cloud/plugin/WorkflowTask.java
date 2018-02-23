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
import alien4cloud.paas.model.PaaSDeploymentContext;
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

    private final int YORC_OPE_TIMEOUT = 1000 * 3600 * 4;  // 4 hours
    
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
        long timeout = System.currentTimeMillis() + YORC_OPE_TIMEOUT;
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
                // Wait Events from Yorc
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
                            // TODO get name of step and stage: need update of Yorc API
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
                status = restClient.getStatusFromYorc(taskUrl);
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
