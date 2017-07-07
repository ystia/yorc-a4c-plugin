/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;

import alien4cloud.paas.model.PaaSDeploymentContext;

/**
 * Information needed for a EventListener Task
 */
public class LogListenerTask extends AlienTask {
    PaaSDeploymentContext ctx;

    public LogListenerTask(PaaSDeploymentContext ctx, JanusPaaSProvider prov) {
        super(prov);
        this.ctx = ctx;
    }

    public void run() {
        orchestrator.listenJanusLogs(this);
    }
}
