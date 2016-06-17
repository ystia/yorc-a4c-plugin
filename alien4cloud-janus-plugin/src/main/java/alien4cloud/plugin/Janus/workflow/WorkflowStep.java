package alien4cloud.plugin.Janus.workflow;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by a628490 on 01/06/2016.
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
