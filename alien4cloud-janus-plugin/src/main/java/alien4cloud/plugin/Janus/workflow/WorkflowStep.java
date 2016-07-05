/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.workflow;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by xBD on 01/06/2016.
 */
@Getter
@Setter
public class WorkflowStep {

    private String workflowId;
    private String workflowStep;
    private Boolean workflowDone;


    /**
     *
     * @param workflowId
     * @param workflowStep
     * @param workflowDone
     */
    public WorkflowStep(String workflowId, String workflowStep, Boolean workflowDone) {
        this.workflowId = workflowId;
        this.workflowStep = workflowStep;
        this.workflowDone = workflowDone;
    }

    public WorkflowStep get(String id, String step){
        if (workflowId == id && step == workflowStep){
            return new WorkflowStep(workflowId, workflowStep, workflowDone);
        }
        return null;
    }
}
