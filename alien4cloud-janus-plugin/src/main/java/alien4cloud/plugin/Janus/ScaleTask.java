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
 * Information needed for a SCALE Task
 */
public class ScaleTask extends AlienTask {
    PaaSDeploymentContext ctx;
    IPaaSCallback<?> callback;
    String node;
    int nbi;

    public ScaleTask(PaaSDeploymentContext ctx, String node, int nbi, IPaaSCallback<?> callback) {
        super(AlienTask.SCALE);
        this.ctx = ctx;
        this.node = node;
        this.nbi = nbi;
        this.callback = callback;
    }
}
