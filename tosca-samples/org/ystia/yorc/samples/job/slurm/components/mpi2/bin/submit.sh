#!/bin/bash
#
#SBATCH --job-name=batch_test
#SBATCH --output=res_mpi.out
#
#SBATCH --ntasks=4
#SBATCH --nodes=4

echo "First srun"
srun --mpi=pmi2 /home_nfs/commons/test.mpi ${NAME1}
echo "Second srun"
srun --mpi=pmi2 /home_nfs/commons/test.mpi ${NAME2}
echo "Third srun"
srun --mpi=pmi2 /home_nfs/commons/test.mpi ${NAME3}
