#!/usr/bin/env bash

echo "${INSTANCE} waiting for results to be ready and displaying"

JOB_TIMESTAMP=$(/bin/date +%s%N)

nohup bash $display_results_script  < /dev/null 2> /tmp/stderr_job_$JOB_TIMESTAMP > /tmp/stdout_job_$JOB_TIMESTAMP &
JOB_PID=$!
export TOSCA_JOB_ID="${JOB_PID}_${JOB_TIMESTAMP}"
export COMMAND_SPAWNED="$COMMAND_TO_SPAWN"

echo "Job $TOSCA_JOB_ID submitted"
