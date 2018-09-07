# Job singularity component sample

This sample illustrates how to write a singularity slurm job component run in a batch or interactive mode (sbatch).

## Run operation implementation

Given the following TOSCA operation definition:
```yaml
      interfaces:
        tosca.interfaces.node.lifecycle.Runnable:
          run:
            inputs:
              exec_command: { get_property: [SELF, exec_command] }
            implementation:
              file: docker://godlovedc/lolcow:latest
              type: yorc.artifacts.Deployment.SlurmJobImage
              repository: docker

      interfaces:
        tosca.interfaces.node.lifecycle.Runnable:
          run:
            inputs:
              exec_command: { get_property: [SELF, exec_command] }
            implementation:
              file: /home_nfs/benoists/hello-world.img
              type: yorc.artifacts.Deployment.SlurmJobImage
              repository: docker

```

## Use private http docker registry
You can use Docker hub public registry or private one.
If your private registry is not accessible from Alien server, you can ignore the resolve artifact step
Given the following TOSCA operation definition:
```yaml
repositories:
  docker:
    url: https://hpda-docker-registry:5000/
    type: a4c_ignore

```

## Outputs & logging

When the program will be run, the following messages will appear in Yorc/Alien logs, in function of the tasks and nodes job properties and in the res_mpi.out file:
The first running job executes a "singularity run docker://godlovedc/lolcow:latest" command and returns the famous cow with random message in interactive mode (slurm srun).
The second executes a "singularity exec /home_nfs/benoists/hello-world.img /usr/bin/hello-kitty.sh" command and returns a simply "hello world" in batch mode (slurm sbatch)
You need to modify the file implementation ans the exec command to run with your specific images and script paths.

```
 _________________________________________
/ You will gain money by a speculation or \
\ lottery.                                /
 -----------------------------------------
        \   ^__^
         \  (oo)\_______
            (__)\       )\/\
                \||      |
                \||     ||

Hello World !`
```

The output files are saved in a root home user directory with name "job_<JOB_ID>_outputs"
