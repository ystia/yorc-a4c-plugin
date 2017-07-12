/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;


public abstract class AlienTask {

    protected JanusPaaSProvider orchestrator;

    /**
     * Constructor
     * @param  provider
     */
    public AlienTask(JanusPaaSProvider provider) {
        this.orchestrator = provider;
    }

    /**
     * Run this task
     */
    public abstract void run();
}