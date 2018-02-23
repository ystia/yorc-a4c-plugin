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
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import lombok.extern.slf4j.Slf4j;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;

import java.util.Hashtable;
import java.util.Map;

/**
 * Operation Task
 */
@Slf4j
public class OperationTask extends AlienTask {
    // Needed Info
    PaaSTopologyDeploymentContext ctx;
    NodeOperationExecRequest request;
    IPaaSCallback<Map<String, String>> callback;

    private final int YORC_OPE_TIMEOUT = 1000 * 3600 * 4;  // 4 hours

    public OperationTask(PaaSTopologyDeploymentContext ctx, YorcPaaSProvider prov, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback) {
        super(prov);
        this.ctx = ctx;
        this.request = request;
        this.callback = callback;
    }

    /**
     * Execute the Operation (Custom Command)
     */
    public void run() {
        Throwable error = null;

        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        YorcRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
        log.info(paasId + " Execute custom command " + request.getOperationName());

        String taskUrl;
        try {
            taskUrl = restClient.postCustomCommandToYorc(deploymentUrl, request);
        } catch (Exception e) {
            orchestrator.sendMessage(paasId, "Custom Command not accepted by Yorc: " + e.getMessage());
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        synchronized (jrdi) {
            // In case we want to undeploy during the custom command.
            jrdi.setDeployTaskId(taskId);
        }
        orchestrator.sendMessage(paasId, "Operation " + request.getOperationName() + " sent to Yorc. taskId=" + taskId);

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
                    error = new Throwable("Operation timeout");
                    break;
                }
                // Wait Events from Yorc
                log.debug(paasId + ": Waiting for Custom command events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for task end");
                    break;
                }
                evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_OPERATION) && evt.getTask_id().equals(taskId)) {
                    jrdi.setLastEvent(null);
                    switch (evt.getStatus()) {
                        case "failed":
                        case "canceled":
                            log.warn("Custom command failed: " + paasId);
                            error = new Exception("Custom command " + request.getOperationName() + " failed");
                            break;
                        case "done":
                            log.debug("Operation success: " + paasId);
                            done = true;
                            break;
                        default:
                            // could be 'initial' or 'running'
                            log.warn("An event has been ignored. Status=" + evt.getStatus());
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
                    log.debug("Operation OK");
                    done = true;
                    break;
                case "FAILED":
                    log.debug("Operation failed");
                    error = new Exception("Operation failed");
                    break;
                default:
                    log.debug("Operation Status is currently " + status);
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
            Map<String, String> customResults = new Hashtable<>(1);
            customResults.put("result", "Succesfully executed custom " + request.getOperationName() + " on node " + request.getNodeTemplateName());
            // TODO Get results returned by the custom command ??
            callback.onSuccess(customResults);
        } else {
            callback.onFailure(error);
        }
    }
}
