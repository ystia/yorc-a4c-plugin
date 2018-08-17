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
                            String paasId = logEvent.getDeploymentId();
                            String deploymentId = orchestrator.getDeploymentId(paasId);
                            log.debug("Received log from Yorc: " + logEvent.toString());
                            log.debug("Received log has deploymentId : " + paasId);
                            if (deploymentId == null) {
                                continue;
                            }
                            // Post a PaaSDeploymentLog to a4c premium log
                            PaaSDeploymentLog paasDeploymentLog = toPaasDeploymentLog(logEvent);
                            paasDeploymentLog.setDeploymentId(deploymentId);
                            paasDeploymentLog.setDeploymentPaaSId(paasId);
                            orchestrator.saveLog(paasDeploymentLog);
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
                        log.error("listenDeploymentEvent wait interrupted", ex);
                    }
                }
            }
        }
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
