/*
* Copyright 2016 Bull Atos.  All Rights Reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
* See the NOTICE file distributed with this work for additional information
* regarding copyright ownership.
*/
package alien4cloud.plugin.Janus.workflow;

import alien4cloud.paas.wf.AbstractStep;
import alien4cloud.paas.wf.Workflow;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xBD on 30/05/2016.
 */
@Slf4j
@Getter
@Setter
public class WorkflowReader {

    public static final String START = "start";
    public static final String CREATE = "create";
    public static final String CONFIGURE = "configure";
    private List<WorkflowStep> workflowSteps;


    /**
     * @param workflow
     */
    public WorkflowReader(Map<String, Workflow> workflow) {
        Map<String, AbstractStep> stepsVal = workflow.get(Workflow.INSTALL_WF).getSteps();
        List<AbstractStep> steps = sortList(stepsVal);
        this.workflowSteps = createWorkflow(steps);
    }

    /**
     * @param steps
     * @return
     */
    private List<WorkflowStep> createWorkflow(List<AbstractStep> steps) {
        List<WorkflowStep> workflowSteps = new ArrayList<>();
        for (AbstractStep step : steps) {
            if (step.getName() != null) {
                String nodeName = step.getName().split("_")[0].replace(" ", "");
                String stepName = step.getName().split("_")[1].replace(" ", "");
//                log.info("node : " + nodeName + " step : " + stepName);

                if (nodeName.equals(START) || nodeName.equals(CREATE) || nodeName.equals(CONFIGURE)) {
                    workflowSteps.add(new WorkflowStep(stepName, nodeName, Boolean.FALSE));
                } else {
                    workflowSteps.add(new WorkflowStep(nodeName, stepName, Boolean.FALSE));
                }
            }
        }
        return workflowSteps;
    }

    /**
     * Kahn's algorithm to sort a list with link between steps
     *
     * @param stepsVal
     * @return
     */
    public List<AbstractStep> sortList(Map<String, AbstractStep> stepsVal) {
        List<AbstractStep> stepInit = new ArrayList();
        List<AbstractStep> following = new ArrayList();
        List<AbstractStep> result = new ArrayList();
        Map<String, AbstractStep> allSteps = new HashMap<>(stepsVal);

        getStartNodes(stepsVal, stepInit, allSteps);
        while (!stepInit.isEmpty()) {
            AbstractStep n = stepInit.get(0);
            result.add(n);
            stepInit.remove(n);
            getFollowingNodes(following, allSteps, n);
            if (following.size() != 0) {
                for (AbstractStep m : following) {
                    AbstractStep stepC = removePrecedingStep(allSteps, n, m);
                    if (allSteps.get(stepC.getName()).getPrecedingSteps().size() == 0) {
                        stepInit.add(stepC);
                    }
                }
            }
            following = new ArrayList<>();
        }
        return result;
    }

    /**
     * @param allSteps
     * @param n
     * @param m
     * @return
     */
    private AbstractStep removePrecedingStep(Map<String, AbstractStep> allSteps, AbstractStep n, AbstractStep m) {
        AbstractStep stepC = allSteps.get(m.getName());
        allSteps.get(stepC.getName()).removePreceding(n.getName());
        return stepC;
    }

    /**
     * @param following
     * @param allSteps
     * @param n
     */
    private void getFollowingNodes(List<AbstractStep> following, Map<String, AbstractStep> allSteps, AbstractStep n) {
        // all steps following n
        if (n.getFollowingSteps() != null) {
            for (String mFollow : n.getFollowingSteps()) {
                following.add(allSteps.get(mFollow));
//                log.info(allSteps.get(mFollow).getName());
            }
        }
    }

    /**
     * @param stepsVal
     * @param stepInit
     * @param allSteps
     */
    private void getStartNodes(Map<String, AbstractStep> stepsVal, List<AbstractStep> stepInit, Map<String, AbstractStep> allSteps) {
        //Step with no preceding steps
        for (AbstractStep step : stepsVal.values()) {
            if (step.getPrecedingSteps() == null) {
                stepInit.add(step);
//                log.info(" Step : " + allSteps.get(step.getName()));
                allSteps.remove(step);
            }
        }
    }

    /**
     * show the workflow steps
     */
    public void read() {
        log.info("__________________________________");
        for (WorkflowStep wf : workflowSteps) {
            log.info(wf.getWorkflowId() + " | " + wf.getWorkflowStep() + " | " + wf.getWorkflowDone());
            log.info("__________________________________");
        }
    }
}
