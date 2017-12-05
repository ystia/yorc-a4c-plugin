/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSInstancePersistentResourceMonitorEvent;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.plugin.Janus.rest.JanusRestException;
import alien4cloud.plugin.Janus.rest.Response.Event;
import alien4cloud.plugin.Janus.rest.Response.EventResponse;
import alien4cloud.plugin.Janus.rest.RestClient;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.MapUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.collect.Maps;

import java.util.Map;
import java.util.UUID;

/**
 * EventListener Task
 */
@Slf4j
public class EventListenerTask extends AlienTask {
    // Possible values for janus event types
    // Check with janus code for these values.
    public static final String EVT_INSTANCE   = "instance";
    public static final String EVT_DEPLOYMENT = "deployment";
    public static final String EVT_OPERATION  = "custom-command";
    public static final String EVT_SCALING    = "scaling";
    public static final String EVT_WORKFLOW   = "workflow";


    public EventListenerTask(JanusPaaSProvider prov) {
        super(prov);
    }

    public void run() {
        int prevIndex = 1;
        while (true) {
            try {
                log.info("Get events from Janus from index " + prevIndex);
                EventResponse eventResponse = restClient.getEventFromJanus(prevIndex);
                if (eventResponse != null) {
                    prevIndex = eventResponse.getLast_index();
                    if (eventResponse.getEvents() != null) {
                        for (Event event : eventResponse.getEvents()) {
                            String paasId = event.getDeployment_id();
                            JanusRuntimeDeploymentInfo jrdi = orchestrator.getDeploymentInfo(paasId);
                            if (jrdi == null) {
                                log.error("listenJanusEvents: no JanusRuntimeDeploymentInfo for " + paasId);
                                return;
                            }
                            Map<String, Map<String, InstanceInformation>> instanceInfo = jrdi.getInstanceInformations();

                            paasId = event.getDeployment_id();
                            // Check type of Event sent by janus and process it
                            String eState = event.getStatus();
                            String eMessage = paasId + " - Janus Event: ";

                            if (event.getType() == null) {
                                log.warn("Janus version is obsolete. Please use a newer version");
                                event.setType(EVT_INSTANCE);
                            }

                            switch (event.getType()) {
                                case EVT_INSTANCE:
                                    String eNode = event.getNode();
                                    String eInstance = event.getInstance();
                                    eMessage += "instance " + eNode + ":" + eInstance + ":" + eState;
                                    log.debug("Received Event from janus <<< " + eMessage);
                                    Map<String, InstanceInformation> ninfo = instanceInfo.get(eNode);
                                    if (ninfo == null) {
                                        // Add a new Node in JanusRuntimeDeploymentInfo
                                        log.debug("Add a node in JanusRuntimeDeploymentInfo: " + eNode);
                                        ninfo = Maps.newHashMap();
                                        instanceInfo.put(eNode, ninfo);
                                    }
                                    InstanceInformation iinfo = ninfo.get(eInstance);
                                    if (iinfo == null) {
                                        // Add a new Instance for this node in JanusRuntimeDeploymentInfo
                                        log.debug("Add an instance in JanusRuntimeDeploymentInfo: " + eInstance);
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
                                            log.warn("Error instance status");
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
                                    log.debug("Received Event from janus <<< " + eMessage);
                                    synchronized (jrdi) {
                                        if (jrdi.getLastEvent() != null) {
                                            log.debug("Event not taken, forgot it: " + jrdi.getLastEvent());
                                        }
                                        jrdi.setLastEvent(event);
                                        jrdi.notifyAll();
                                    }
                                    break;
                                default:
                                    log.warn("Unknown event type received from janus <<< " + event.getType());
                                    break;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                log.error("listenDeploymentEvent Stopped");
                return;
            } catch (Exception e) {
                log.warn("listenDeploymentEvent Failed", e);
                return;
            }
        }
    }

}
