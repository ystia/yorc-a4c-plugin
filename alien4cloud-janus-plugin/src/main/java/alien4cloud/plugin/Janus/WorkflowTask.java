/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;

import java.util.Map;

/**
 * Information needed for a WORKFLOW Task
 */
public class WorkflowTask extends AlienTask {
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;
    String workflowName;
    Map<String, Object> inputs;

    public WorkflowTask(PaaSDeploymentContext ctx, String workflowName, Map<String, Object> inputs, IPaaSCallback<?> callback) {
        super(AlienTask.WORKFLOW);
        this.ctx = ctx;
        this.workflowName = workflowName;
        this.inputs = inputs;
        this.callback = callback;
    }
}
