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
    private final int WAIT_EVENT_TIMEOUT = 1000 * 30; // 30 seconds

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
        String taskUrl = null;
        boolean done = false;
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
                    jrdi.wait(WAIT_EVENT_TIMEOUT);
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

        log.debug("Undeploying with purge deployment Id:" + paasId);
        try {
            taskUrl = restClient.undeploy(deploymentUrl, true);
            if (taskUrl == null) {
                // Assumes already undeployed
                orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                done = true;
            }
        }
        catch(YorcRestException jre){
            // If 400 code (bad request)  is returned, we retry requesting purge during at most 5 minutes
            //TODO Do we still need to retry the undeployment with purge ?
            if (jre.getHttpStatusCode() == 400) {
                long timeout = System.currentTimeMillis() + 1000 * 60 * 5;
                long timetowait = timeout - System.currentTimeMillis();
                boolean retry = true;
                while (retry && timetowait > 0) {
                    retry = retryDeploymentPurge(paasId);
                }
            }
            // Deployment is not found or already undeployed
            else if (jre.getHttpStatusCode() == 404){
                orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                done = true;
            }
            else {
                log.error("undeploy purge returned an exception: " + jre.getMessage());
                orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
                error = jre;
            }
        }
        catch(Exception e){
            log.error("undeploy purge returned an exception: " + e.getMessage());
            orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
            error = e;
        }

        if (!done && error == null) {
            String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
            orchestrator.sendMessage(paasId, "Undeployment sent to Yorc. taskId=" + taskId);

            // wait for Yorc undeployment completion
            long timeout = System.currentTimeMillis() + YORC_UNDEPLOY_TIMEOUT;
            String status;
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
                        done = true;
                        break;
                    case "INITIAL":
                        // No event will be received, and the undeployment should be straightforward
                        timeout = System.currentTimeMillis() + 3000;
                        break;
                    default:
                        log.debug("Deployment Status is currently " + status);
                        break;
                }
                if (done) {
                    // No need to wait for an event, the status is already undeployed
                    break;
                }
                // Wait an Event from Yorc or timeout
                synchronized (jrdi) {
                    long remainingTime = timeout - System.currentTimeMillis();
                    if (remainingTime <= 0) {
                        log.warn("Timeout occured");
                        break;
                    }
                    try {
                        // Not waiting for the overall remaining time here,
                        // as the plugin may never be notified of the undeployment
                        // if Yorc purge has deleted all events before clients
                        // listening to events were notified.
                        // Waiting for a shorter time here.
                        // If no event is received, we'll go back at the beginning
                        // of the loop and check the deployment existence and status
                        // and wait again here if needed
                        long waitTime = WAIT_EVENT_TIMEOUT;
                        if (waitTime >  remainingTime) {
                            waitTime = remainingTime;
                        }
                        jrdi.wait(waitTime);
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
            orchestrator.removeDeploymentInfo(paasId);
            callback.onSuccess(null);
        } else {
            callback.onFailure(error);
        }
    }

    /**
     * Retry deployment purge
     * @param paasId
     * @return boolean
     */
    private boolean retryDeploymentPurge(String paasId) {
        boolean retry = false;
        try{
            // Wait for 2 seconds before retrying purge request
            Thread.sleep(2000L);
            restClient.undeploy("/deployments/" + paasId, true);
        }
        catch (InterruptedException ex) {
            log.error("Waiting for purge requesting has been interrupted", ex);
            retry = true;
        }
        catch(YorcRestException jre){
            if (jre.getHttpStatusCode() == 400){
                log.warn("Purge will be requested again later because Yorc is still undeploying application with deployment id:" + paasId);
                retry = true;
            }
            // 404 status code is ignored for purge failure
            else if (jre.getHttpStatusCode() != 404){
                log.error("undeploy purge returned an exception: " + jre.getMessage());
                orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
            }
        }
        catch(Exception e){
            log.error("undeploy purge returned an exception: " + e.getMessage());
            orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
        }
        return retry;
    }
}


