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

import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSInstancePersistentResourceMonitorEvent;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.MapUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.collect.Maps;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.Event;
import org.ystia.yorc.alien4cloud.plugin.rest.Response.EventResponse;

import java.util.Map;
import java.util.UUID;

/**
 * EventListener Task
 */
@Slf4j
public class EventListenerTask extends AlienTask {
    // Possible values for Yorc event types
    // Check with Yorc code for these values.
    public static final String EVT_INSTANCE   = "instance";
    public static final String EVT_DEPLOYMENT = "deployment";
    public static final String EVT_OPERATION  = "custom-command";
    public static final String EVT_SCALING    = "scaling";
    public static final String EVT_WORKFLOW   = "workflow";

    // Set this to false to stop pollong events
    private boolean valid = true;


    public EventListenerTask(YorcPaaSProvider prov) {
        super(prov);
    }

    public void stop() {
        valid = false;
    }

    public void run() {
        int prevIndex = 1;
        while (valid) {
            try {
                log.debug("Get events from Yorc from index " + prevIndex);
                EventResponse eventResponse = restClient.getEventFromYorc(prevIndex);
                if (eventResponse != null) {
                    prevIndex = eventResponse.getLast_index();
                    if (eventResponse.getEvents() != null) {
                        for (Event event : eventResponse.getEvents()) {
                            String paasId = event.getDeployment_id();
                            log.debug("Received event from Yorc: " + event.toString());
                            log.debug("Received event has deploymentId : " + paasId);
                            String deploymentId = orchestrator.getDeploymentId(paasId);
                            if (deploymentId == null) {
                                continue;
                            }
                            YorcRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
                            Map<String, Map<String, InstanceInformation>> instanceInfo = jrdi.getInstanceInformations();

                            if (jrdi == null) {
                                log.error("listenYorcEvents: no YorcRuntimeDeploymentInfo for " + paasId);
                                continue;
                            }

                            // Check type of Event sent by Yorc and process it
                            String eState = event.getStatus();
                            String eMessage = paasId + " - Yorc Event: ";

                            if (event.getType() == null) {
                                log.warn("Yorc version is obsolete. Please use a newer version");
                                event.setType(EVT_INSTANCE);
                            }

                            switch (event.getType()) {
                                case EVT_INSTANCE:
                                    String eNode = event.getNode();
                                    String eInstance = event.getInstance();
                                    eMessage += "instance " + eNode + ":" + eInstance + ":" + eState;
                                    log.debug("Received Event from Yorc <<< " + eMessage);
                                    Map<String, InstanceInformation> ninfo = instanceInfo.get(eNode);
                                    if (ninfo == null) {
                                        // Add a new Node in YorcRuntimeDeploymentInfo
                                        log.debug("Add a node in YorcRuntimeDeploymentInfo: " + eNode);
                                        ninfo = Maps.newHashMap();
                                        instanceInfo.put(eNode, ninfo);
                                    }
                                    InstanceInformation iinfo = ninfo.get(eInstance);
                                    if (iinfo == null) {
                                        // Add a new Instance for this node in YorcRuntimeDeploymentInfo
                                        log.debug("Add an instance in YorcRuntimeDeploymentInfo: " + eInstance);
                                        iinfo = orchestrator.newInstance(new Integer(eInstance));
                                        ninfo.put(eInstance, iinfo);
                                    }
                                    orchestrator.updateInstanceState(paasId, eNode, eInstance, iinfo, eState);
                                    switch (eState) {
                                        case "initial":
                                        case "creating":
                                        case "deleting":
                                        case "starting":
                                        case "stopping":
                                        case "configured":
                                        case "configuring":
                                        case "created":
                                            break;
                                        case "deleted":
                                            ninfo.remove(eInstance);
                                            break;
                                        case "stopped":
                                            orchestrator.updateInstanceAttributes(paasId, iinfo, eNode, eInstance);
                                            break;
                                        case "started":
                                            orchestrator.updateInstanceAttributes(paasId, iinfo, eNode, eInstance);
                                            // persist BS Id
                                            String source = jrdi.getDeploymentContext().getDeployment().getSourceName();
                                            if (source.equals("BLOCKSTORAGE_APPLICATION")) {
                                                PaaSInstancePersistentResourceMonitorEvent prme = new PaaSInstancePersistentResourceMonitorEvent(eNode, eInstance,
                                                        MapUtil.newHashMap(new String[]{NormativeBlockStorageConstants.VOLUME_ID}, new Object[]{UUID.randomUUID().toString()}));
                                                orchestrator.postEvent(prme, paasId);
                                            }
                                            break;
                                        case "error":
                                            log.warn("Error instance status in deploymentID: {} and nodeID: {}", paasId, eNode);
                                            break;
                                        default:
                                            log.warn("Unknown instance status: " + eState);
                                            break;
                                    }
                                    break;
                                case EVT_DEPLOYMENT:
                                case EVT_OPERATION:
                                case EVT_SCALING:
                                case EVT_WORKFLOW:
                                    eMessage += event.getType() + ":" + eState;
                                    log.debug("Received Event from Yorc <<< " + eMessage);
                                    synchronized (jrdi) {
                                        if (jrdi.getLastEvent() != null) {
                                            log.debug("Event not taken, forgot it: " + jrdi.getLastEvent());
                                        }
                                        jrdi.setLastEvent(event);
                                        jrdi.notifyAll();
                                    }
                                    break;
                                default:
                                    log.warn("Unknown event type received from Yorc <<< " + event.getType());
                                    break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (valid) {
                    log.error("listenDeploymentEvent Failed", e);
                    try {
                        // We will sleep for 2sec in order to limit logs flood if the yorc server went down
                        Thread.sleep(2000L);
                    } catch (InterruptedException ex) {
                        log.error("listenDeploymentEvent wait interrupted", ex);
                    }
                }
            }
        }
    }

}
