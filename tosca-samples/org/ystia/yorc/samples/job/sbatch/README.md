# Job sbatch component sample

This sample illustrates how to write a slurm job component run in a batch mode (sbatch).
They're 3 identical MPI programs which just print `hello <ARG>` or `hello world` without arguments.
It's allows using sbatch script with defined parameters as ntasks, output, nodes and so on...
There is a 30s delay to allow retrieving job information (job ID and job state) during the job execution.

## Run operation implementation

This sample demonstrates how to provide run operation implementation with the executable run by the job in batch mode.

Given the following TOSCA operation definition:
```yaml
    interfaces:
      tosca.interfaces.node.lifecycle.Runnable:
        run:
          implementation:
            file: bin/submit.sh
            type: yorc.artifacts.Deployment.SlurmJobBin
```

A SBATCH script needs to be provided.
The implementation type must be `yorc.artifacts.Deployment.SlurmJobBin`.


## Outputs & logging

The program is 30s delayed in order to allow retrieving job information as JobID and job state.
This information will appear periodically in the Yorc/Alien logs:
`Job ID:3821, Job State:RUNNING`

When the program will be run, the following messages will appear in Yorc/Alien logs, in function of the tasks and nodes job properties and in the res_mpi.out file:

`First srun
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
 Hello fred!  I am process number: 3 on host hpda16`

The output files are saved in a root home user directory with name "job_<JOB_ID>_outputs"