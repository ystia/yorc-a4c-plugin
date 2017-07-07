/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus;


public abstract class AlienTask {

    // type of Task
    final static int DEPLOY = 1;
    final static int UNDEPLOY = 2;
    final static int SCALE = 3;
    final static int OPERATION = 4;
    final static int WORKFLOW = 5;
    protected int type;

    protected JanusPaaSProvider orchestrator;

    /**
     * Constructor
     * @param type
     */
    public AlienTask(int type, JanusPaaSProvider provider) {
        this.orchestrator = provider;
        this.type = type;
    }

    /**
     * Run this task
     */
    public abstract void run();
}