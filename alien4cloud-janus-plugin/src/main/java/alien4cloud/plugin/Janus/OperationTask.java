/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

import java.util.Map;

/**
 * Information needed for an OPERATION Task
 */
public class OperationTask extends AlienTask {
    PaaSTopologyDeploymentContext ctx;
    NodeOperationExecRequest request;
    IPaaSCallback<Map<String, String>> callback;

    public OperationTask(PaaSTopologyDeploymentContext ctx, JanusPaaSProvider prov, NodeOperationExecRequest request, IPaaSCallback<Map<String, String>> callback) {
        super(AlienTask.OPERATION, prov);
        this.ctx = ctx;
        this.request = request;
        this.callback = callback;
    }

    public void run() {
        orchestrator.doExecuteOperation(this);
    }
}
