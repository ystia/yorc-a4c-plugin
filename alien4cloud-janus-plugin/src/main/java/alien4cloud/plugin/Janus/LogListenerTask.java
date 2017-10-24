/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import alien4cloud.plugin.Janus.rest.JanusRestException;
import alien4cloud.plugin.Janus.rest.Response.LogEvent;
import alien4cloud.plugin.Janus.rest.Response.LogResponse;
import alien4cloud.plugin.Janus.rest.RestClient;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * EventListener Task
 */
@Slf4j
public class LogListenerTask extends AlienTask {
    // Needed Info
    PaaSDeploymentContext ctx;

    public LogListenerTask(PaaSDeploymentContext ctx, JanusPaaSProvider prov) {
        super(prov);
        this.ctx = ctx;
    }

    /**
     * Listen for Janus Logs
     */
    public void run() {
        String paasId = ctx.getDeploymentPaaSId();
        String deploymentUrl = "/deployments/" + paasId;
        int prevIndex = 1;
        while (true) {
            try {
                LogResponse logResponse = restClient.getLogFromJanus(deploymentUrl, prevIndex);
                if (logResponse != null) {
                    prevIndex = logResponse.getLast_index();
                    if (logResponse.getLogs() != null) {
                        for (LogEvent logEvent : logResponse.getLogs()) {
                            log.debug("Received log from janus: " + logEvent.toString());
                            // add Premium Log
                            postLog(toPaasDeploymentLog(logEvent), paasId);
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.error("listenJanusLog Stopped " + paasId);
                return;
            } catch (JanusRestException e) {
                if (e.getHttpStatusCode() == 404) {
                    log.warn("Stop listening to logs. Assuming " + paasId + " is undeployed.");
                } else {
                    log.warn("listenJanusLog Failed " + paasId, e);
                }
                return;
            } catch (Exception e) {
                log.warn("listenJanusLog Failed " + paasId, e);
                return;
            }
        }
    }
    /**
     * Post a PaaSDeploymentLog to a4c premium log
     * @param pdlog
     * @param paasId
     */
    private void postLog(PaaSDeploymentLog pdlog, String paasId) {
        // The DeploymentId is overridden by A4C plugin here with UUID
        pdlog.setDeploymentId(orchestrator.getDeploymentId(paasId));
        pdlog.setDeploymentPaaSId(paasId);
        if (pdlog.getDeploymentId() == null) {
            log.error("Must provide an Id for this log: " + pdlog.toString());
            Thread.dumpStack();
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

}
