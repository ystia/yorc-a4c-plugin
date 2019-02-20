# Job simple component sample

This sample illustrates how to write a TOSCA component allowing to run a job using the Slurm job scheduler (sbatch).
It provides a simple command to run without operation implementation artifact provided.
The command is "srun --mpi=pmi2 test.mpi john" and the non provided artifact binary (test.mpi) is expected to be present in the home user directory.



## Outputs & logging

The program is 30s delayed in order to allow retrieving job information as JobID and job state.
This information will appear periodically in the Yorc/Alien logs:
`Job ID:3821, Job State:RUNNING`

When the program will be run, the following messages will appear in Yorc/Alien logs, in function of the tasks and nodes job properties and in the slurm output file:

```
Hello john!  I am process number: 1 on host hpda16
Hello john!  I am process number: 0 on host hpda16
Hello john!  I am process number: 2 on host hpda16
Hello john!  I am process number: 3 on host hpda16
```

The output files are saved in a root home user directory with name `job_<JOB_ID>_outputs`