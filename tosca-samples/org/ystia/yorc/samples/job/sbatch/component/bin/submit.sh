#!/bin/bash
#
#SBATCH --job-name=batch_test
#SBATCH --output=res_mpi.out
#
#SBATCH --ntasks=4
#SBATCH --nodes=4

echo "First srun"
srun --mpi=pmi2 test1.mpi ${NAME1}
echo "Second srun"
srun --mpi=pmi2 test2.mpi ${NAME2}
echo "Third srun"
srun --mpi=pmi2 test3.mpi ${NAME3}
