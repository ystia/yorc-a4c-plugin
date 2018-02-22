/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package org.ystia.yorc.alien4cloud.plugin;

import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.paas.model.PaaSDeploymentLogLevel;
import lombok.extern.slf4j.Slf4j;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogEvent;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogResponse;

/**
 * EventListener Task
 */
@Slf4j
public class LogListenerTask extends AlienTask {

    public LogListenerTask(YorcPaaSProvider prov) {
        super(prov);
    }

    /**
     * Listen for Yorc Logs
     */
    public void run() {
        int prevIndex = 1;
        while (true) {
            try {
                log.debug("Get logs from Yorc from index " + prevIndex);
                LogResponse logResponse = restClient.getLogFromYorc(prevIndex);
                if (logResponse != null) {
                    prevIndex = logResponse.getLast_index();
                    if (logResponse.getLogs() != null) {
                        for (LogEvent logEvent : logResponse.getLogs()) {
                            log.debug("Received log from Yorc: " + logEvent.toString());
                            // add Premium Log
                            postLog(toPaasDeploymentLog(logEvent), logEvent.getDeploymentId());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("listen Yorc Logs Failed", e);
                try {
                    // We will sleep for 2sec in order to limit logs flood if the Yorc server went down
                    Thread.sleep(2000L);
                } catch (InterruptedException ex) {
                    log.error("listenDeploymentEvent wait interrupted", ex);
                }
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
