# Job sbatch component sample

This sample illustrates how to write a TOSCA component allowing to run a batch job using the Slurm job scheduler (sbatch).
It provides a submission script that contains 3 identical MPI programs which just print `hello <ARG>` or `hello world` without arguments.
The sbatch script contains directives such as ntasks, output, nodes and so on...
There is a 30s delay to allow retrieving job information (job ID and job state) during the job execution.

## Submit operation implementation

This sample show how to define a submit operation implementation with the executable submit.sh submission script.

Given the following TOSCA operation definition:
```yaml
    interfaces:
      tosca.interfaces.node.lifecycle.Runnable:
        submit:
          implementation:
            file: bin/submit.sh
            type: yorc.artifacts.Deployment.SlurmJobBin
```

A submission script needs to be provided.
The implementation type must be `yorc.artifacts.Deployment.SlurmJobBin`.

## Outputs & logging

The program is 30s delayed in order to allow retrieving job information as JobID and job state.
This information will appear periodically in the Yorc/Alien logs:
`Job ID:3821, Job State:RUNNING`

When the program will be run, the following messages will appear in Yorc/Alien logs, in function of the tasks and nodes job properties and in the res_mpi.out file:

```
First srun
Hello john!  I am process number: 1 on host hpda16
Hello john!  I am process number: 0 on host hpda16
Hello john!  I am process number: 2 on host hpda16
Hello john!  I am process number: 3 on host hpda16
Second srun
Hello mary!  I am process number: 0 on host hpda16
Hello mary!  I am process number: 1 on host hpda16
Hello mary!  I am process number: 2 on host hpda16
Hello mary!  I am process number: 3 on host hpda16
Third srun
Hello fred!  I am process number: 0 on host hpda16
Hello fred!  I am process number: 1 on host hpda16
Hello fred!  I am process number: 2 on host hpda16
Hello fred!  I am process number: 3 on host hpda16
```

The output files are saved in a root home user directory with name `job_<JOB_ID>_outputs`