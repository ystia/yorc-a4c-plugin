#!/usr/bin/env bash

echo "Submitting job..."
export TOSCA_JOB_ID="Job-${RANDOM}"

JOB_TIMESTAMP=`/bin/date +%s%N`

nohup bash -c "$COMMAND_TO_SPAWN"  < /dev/null 2> /tmp/stderr_job_$JOB_TIMESTAMP > /tmp/stdout_job_$JOB_TIMESTAMP &
JOB_PID=$!
export TOSCA_JOB_ID="${JOB_PID}_${JOB_TIMESTAMP}"

echo "Job $TOSCA_JOB_ID submitted"

