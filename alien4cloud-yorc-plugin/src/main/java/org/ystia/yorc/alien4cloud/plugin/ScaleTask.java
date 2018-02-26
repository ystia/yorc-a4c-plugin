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

/**
 * Scaling Task
 */
@Slf4j
public class ScaleTask extends AlienTask {
    // Needed info
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;
    String node;
    int nbi;

    private final int YORC_SCALE_TIMEOUT = 1000 * 3600 * 4;  // 4 hours

    public ScaleTask(PaaSDeploymentContext ctx, YorcPaaSProvider prov, String node, int nbi, IPaaSCallback<?> callback) {
        super(prov);
        this.ctx = ctx;
        this.node = node;
        this.nbi = nbi;
        this.callback = callback;
    }

    /**
     * Execute the Scaling
     */
    public void run() {
        Throwable error = null;

        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        YorcRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
        log.info(paasId + " : scaling " + node + " delta=" + nbi);

        String taskUrl;
        try {
            taskUrl = restClient.postScalingToYorc(deploymentUrl, node, nbi);
        } catch (Exception e) {
            orchestrator.sendMessage(paasId, "Scaling not accepted by Yorc: " + e.getMessage());
            callback.onFailure(e);
            return;
        }
        String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
        synchronized (jrdi) {
            // In case we want to undeploy during the scale.
            jrdi.setDeployTaskId(taskId);
        }
        orchestrator.sendMessage(paasId, "Scaling sent to Yorc. taskId=" + taskId);

        // wait for end of task
        boolean done = false;
        long timeout = System.currentTimeMillis() + YORC_SCALE_TIMEOUT;
        Event evt;
        while (!done && error == null) {
            synchronized (jrdi) {
                // Check for timeout
                long timetowait = timeout - System.currentTimeMillis();
                if (timetowait <= 0) {
                    log.warn("Timeout occured");
                    error = new Throwable("Scaling timeout");
                    break;
                }
                // Wait Events from Yorc
                log.debug(paasId + ": Waiting for scaling events.");
                try {
                    jrdi.wait(timetowait);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for scaling");
                }
                // Check if we received a Scaling Event and process it
                evt = jrdi.getLastEvent();
                if (evt != null && evt.getType().equals(EventListenerTask.EVT_SCALING)) {
                    jrdi.setLastEvent(null);
                    switch (evt.getStatus()) {
                        case "failed":
                            log.warn("Scaling failed: " + paasId);
                            error = new Exception("Scaling failed");
                            break;
                        case "canceled":
                            log.warn("Scaling canceled: " + paasId);
                            error = new Exception("Scaling canceled");
                            break;
                        case "done":
                            log.debug("Scaling success: " + paasId);
                            done = true;
                            break;
                        default:
                            orchestrator.sendMessage(paasId, "Scaling status = " + evt.getStatus());
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
                    log.debug("Scaling OK");
                    done = true;
                    break;
                case "FAILED":
                    log.debug("Scaling failed");
                    error = new Exception("Scaling failed");
                    break;
                default:
                    log.debug("Scaling Status is currently " + status);
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