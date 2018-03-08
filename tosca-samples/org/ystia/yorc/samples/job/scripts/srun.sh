#!/usr/bin/env bash


srun --mpi=pmi2 --job-name=test_mpi --ntasks=${n_tasks} --nodes=${n_nodes} test.mpi