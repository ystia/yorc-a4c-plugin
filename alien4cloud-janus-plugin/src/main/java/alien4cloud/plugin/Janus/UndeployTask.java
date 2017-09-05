/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.RestClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Undeployment Task
 */
@Slf4j
public class UndeployTask extends AlienTask {
    // needed info
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;

    private final int JANUS_UNDEPLOY_TIMEOUT = 1000 * 60 * 30;  //  30 mn

    public UndeployTask(PaaSDeploymentContext ctx, JanusPaaSProvider prov, IPaaSCallback<?> callback) {
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
        JanusRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);

        // first check if a deployment is still running
        synchronized (jrdi) {
            if (jrdi.getDeployTaskId() != null) {
                // must stop it and wait for the janus task done
                try {
                    restClient.stopTask(deploymentUrl + "/tasks/" + jrdi.getDeployTaskId());
                    // do not wait more than 30 sec.
                    jrdi.wait(1000 * 30);
                } catch (Exception e) {
                    log.error("stopTask returned an exception", e);
                }
                // Maybe janus is stuck. Forget the task and continue.
                if (jrdi.getDeployTaskId() != null) {
                    jrdi.setDeployTaskId(null);
                    log.warn("A deployment task was stuck. Forget it.");
                }
            }
        }

        log.debug("Undeploying " + paasId);
        boolean done = false;
        String taskUrl = null;
        String status = "UNKNOWN";
        try {
            taskUrl = restClient.undeployJanus(deploymentUrl);
            if (taskUrl == null) {
                // Assumes already undeployed
                status = "UNDEPLOYED";
                orchestrator.changeStatus(paasId, DeploymentStatus.UNDEPLOYED);
                done = true;
            }
        } catch (Exception e) {
            log.debug("undeployJanus returned an exception: " + e);
            orchestrator.changeStatus(paasId, DeploymentStatus.FAILURE);
            error = e;
            done = true;
        }
        if (! done) {
            String taskId = taskUrl.substring(taskUrl.lastIndexOf("/") + 1);
            orchestrator.sendMessage(paasId, "Undeployment sent to Janus. taskId=" + taskId);

            // wait for janus undeployment completion
            long timeout = System.currentTimeMillis() + JANUS_UNDEPLOY_TIMEOUT;
            Event evt;
            while (!done && error == null) {
                // Check if already done
                // This may occur when undeploy is immediate
                try {
                    status = restClient.getStatusFromJanus(deploymentUrl);
                } catch (Exception e) {
                    // TODO Check error 404
                    // assumes it is undeployed
                    status = "UNDEPLOYED";
                }
                log.debug("Status of deployment: " + status);
                switch (status) {
                    case "UNDEPLOYED":
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
                // Wait an Event from Janus or timeout
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
