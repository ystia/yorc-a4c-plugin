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
                // Check deployment timeout
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Deployment Timeout occured");
                    error = new Throwable("Deployment timeout");
                    orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                    break;
                }
                // Wait Deployment Events from Yorc
                log.debug(paasId + ": Waiting for deployment events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for deployment");
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
