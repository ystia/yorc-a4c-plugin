/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;

/**
 * Information needed for a UNDEPLOY Task
 */
public class UndeployTask extends AlienTask {
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;

    public UndeployTask(PaaSDeploymentContext ctx, JanusPaaSProvider prov, IPaaSCallback<?> callback) {
        super(prov);
        this.ctx = ctx;
        this.callback = callback;
    }

    public void run() {
        orchestrator.doUndeploy(this);
    }
}
