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
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import lombok.extern.slf4j.Slf4j;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;
import org.ystia.yorc.alien4cloud.plugin.rest.YorcRestException;

/**
 * Undeployment Task
 */
@Slf4j
public class UndeployTask extends AlienTask {
    // needed info
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;

    private final int YORC_UNDEPLOY_TIMEOUT = 1000 * 60 * 30;  //  30 mn

    public UndeployTask(PaaSDeploymentContext ctx, YorcPaaSProvider prov, IPaaSCallback<?> callback) {
        super(prov);
        this.ctx = ctx;
        this.callback = callback;
    }

    /**
     * Execute the Undeployment
     */
    public void run() {
        Throwable error = null;

        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        YorcRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);

        // first check if a deployment is still running
        synchronized (jrdi) {
            if (jrdi.getDeployTaskId() != null) {
                // must stop it and wait until yorc returns the task is done
                try {
                    restClient.stopTask(deploymentUrl + "/tasks/" + jrdi.getDeployTaskId());
                    // do not wait more than 30 sec.
                    jrdi.wait(1000 * 30);
                } catch (Exception e) {
                    log.error("stopTask returned an exception", e);
                }
                // Maybe Yorc is stuck. Forget the task and continue.
                if (jrdi.getDeployTaskId() != null) {
                    jrdi.setDeployTaskId(null);
                    log.warn("A deployment task was stuck. Forget it.");
                }
            }
        }

        log.debug("Undeploying deployment Id:" + paasId);
        boolean done = false;
        String taskUrl = null;
        String status;
        try {
            taskUrl = restClient.undeploy(deploymentUrl, false);
            if (taskUrl == null) {
                // Assumes already undeployed
                orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                done = true;
            }
        }
        catch (YorcRestException jre) {
            // Deployment is not found or already undeployed
            if (jre.getHttpStatusCode() == 404 || jre.getHttpStatusCode() == 400 ) {
                orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                done = true;
            } else {
                log.debug("undeploy returned an exception: " + jre.getMessage());
                orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
                error = jre;
            }
        }
        catch (Exception e) {
            log.error("undeploy returned an exception: " + e);
            orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
            error = e;
        }
        if (! done && error == null) {
            String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
            orchestrator.sendMessage(paasId, "Undeployment sent to Yorc. taskId=" + taskId);

            // wait for Yorc undeployment completion
            long timeout = System.currentTimeMillis() + YORC_UNDEPLOY_TIMEOUT;
            Event evt;
            while (!done && error == null) {
                // Check if already done
                // This may occur when undeploy is immediate
                try {
                    status = restClient.getStatusFromYorc(deploymentUrl);
                }
                catch (YorcRestException jre){
                    if (jre.getHttpStatusCode() == 404){
                        // assumes it is undeployed
                        status = "UNDEPLOYED";
                    } else {
                        log.error("undeploy returned an exception: " + jre.getMessage());
                        orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
                        error = jre;
                        break;
                    }
                }
                catch (Exception e) {
                    log.error("undeploy returned an exception: " + e.getMessage());
                    orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
                    error = e;
                    break;
                }
                log.debug("Status of deployment: " + status);
                switch (status) {
                    case "UNDEPLOYED":
                        log.debug("Undeployment OK");
                        // Undeployment OK.
                        orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                        break;
                    case "INITIAL":
                        // No event will be received, and the undeployment should be straightforward
                        timeout = System.currentTimeMillis() + 3000;
                        break;
                    default:
                        log.debug("Deployment Status is currently " + status);
                        break;
                }
                // Wait an Event from Yorc or timeout
                synchronized (jrdi) {
                    long timetowait = timeout - System.currentTimeMillis();
                    if (timetowait <= 0) {
                        log.warn("Timeout occured");
                        break;
                    }
                    try {
                        jrdi.wait(timetowait);
                    } catch (InterruptedException e) {
                        log.error("Interrupted while waiting for undeployment");
                        break;
                    }
                    evt = jrdi.getLastEvent();
                    if (evt != null && evt.getType().equals(EventListenerTask.EVT_DEPLOYMENT)) {
                        jrdi.setLastEvent(null);
                        switch (evt.getStatus()) {
                            case "undeployment_failed":
                                log.warn("Undeployment failed: " + paasId);
                                orchestrator.doChangeStatus(paasId, DeploymentStatus.FAILURE);
                                error = new Exception("Undeployment failed");
                                break;
                            case "undeployed":
                                log.debug("Undeployment success: " + paasId);
                                orchestrator.doChangeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                                done = true;
                                break;
                            case "undeploying":
                            case "undeployment_in_progress":
                                orchestrator.doChangeStatus(paasId, DeploymentStatus.UNDEPLOYMENT_IN_PROGRESS);
                                break;
                            default:
                                orchestrator.sendMessage(paasId, "Undeployment: status=" + evt.getStatus());
                                break;
                        }
                    }
                }
            }
        }
        // Return result to a4c
        if (error == null) {
            callback.onSuccess(null);
            orchestrator.removeDeploymentInfo(paasId);
        } else {
            callback.onFailure(error);
        }
    }
}
