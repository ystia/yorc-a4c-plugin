# Job component sample

This sample illustrates how to write a slurm job component run interactively (srun).
It's MPI program which just print `hello <ARG>` or `hello world` without arguments.
It's allows running a MPI job across several processes with the job defined `task` property.
There is a 30s delay to allow retrieving job information (job ID and job state) during the job execution.

## Run operation implementation

This sample demonstrates how to provide run operation implementation with the executable run by the job and how to retrieve arguments from the job exec_arg property.

Given the following TOSCA operation definition:
```yaml
    interfaces:
      tosca.interfaces.node.lifecycle.Runnable:
        run:
          inputs:
            args: {get_property: [SELF, exec_args]}
          implementation:
            file: bin/test.mpi
            type: yorc.artifacts.Deployment.SlurmJobBin
```

An executable file needs to be provided.
The implementation type must be `yorc.artifacts.Deployment.SlurmJobBin`.


## Outputs & logging

The program is 30s delayed in order to allow retrieving job information as JobID and job state.
This information will appear periodically in the Yorc/Alien logs:
`Job ID:3821, Job State:RUNNING`

When the program will be run, the following messages will appear in Yorc/Alien logs, in function of the tasks and nodes job properties:

`Hello Slurm! I am process number: 0 on host <HOSTNAME>
 Hello Slurm! I am process number: 1 on host <HOSTNAME>
 Hello Slurm! I am process number: 2 on host <HOSTNAME>
 Hello Slurm! I am process number: 3 on host <HOSTNAME>`
