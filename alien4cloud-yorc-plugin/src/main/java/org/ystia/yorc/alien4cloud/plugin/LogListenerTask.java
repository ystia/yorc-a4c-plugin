/**
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ystia.yorc.alien4cloud.plugin;

import alien4cloud.paas.model.*;
import lombok.extern.slf4j.Slf4j;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogEvent;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.LogResponse;


/**
 * EventListener Task
 */
@Slf4j
public class LogListenerTask extends AlienTask {

    // Set this to false to stop pollong events
    private boolean valid = true;


    public LogListenerTask(YorcPaaSProvider prov) {
        super(prov);
    }

    public void stop() {
        valid = false;
    }


    /**
     * Listen for Yorc Logs
     */
    public void run() {
        int prevIndex = 1;
        while (valid) {
            try {
                log.debug("Get logs from Yorc from index " + prevIndex);
                LogResponse logResponse = restClient.getLogFromYorc(prevIndex);
                if (logResponse != null) {
                    prevIndex = logResponse.getLast_index();
                    if (logResponse.getLogs() != null) {
                        for (LogEvent logEvent : logResponse.getLogs()) {
                            log.debug("Received log from Yorc: " + logEvent.toString());
                            // add Premium Log
                            PaaSDeploymentLog pLog = toPaasDeploymentLog(logEvent);
                            postLog(pLog, logEvent.getDeploymentId());
                        }
                    }
                }
            } catch (Exception e) {
                if (valid) {
                    log.warn("listen Yorc Logs Failed", e);
                    try {
                        // We will sleep for 2sec in order to limit logs flood if the Yorc server went down
                        Thread.sleep(2000L);
                    } catch (InterruptedException ex) {
                        log.warn("listenDeploymentEvent wait interrupted ({})", ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Post a PaaSDeploymentLog to a4c premium log
     *
     * @param pdlog
     * @param paasId
     */
    private void postLog(PaaSDeploymentLog pdlog, String paasId) {
        // The DeploymentId is overridden by A4C plugin here with UUID
        pdlog.setDeploymentId(orchestrator.getDeploymentId(paasId));
        pdlog.setDeploymentPaaSId(paasId);
        if (pdlog.getDeploymentId() == null) {
            log.warn("Must provide an Id for this log: " + pdlog.toString());
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
        // we use the raw timestamp that is a nanosec precision to ease the sort of logs
        deploymentLog.setRawtimestamp(pLogEvent.getTimestamp());
        deploymentLog.setWorkflowId(pLogEvent.getWorkflowId());
        deploymentLog.setOperationName(pLogEvent.getOperationName());
        deploymentLog.setTaskId(pLogEvent.getAlienTaskId());
        return deploymentLog;
    }
}
