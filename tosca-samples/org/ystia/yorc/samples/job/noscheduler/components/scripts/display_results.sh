#!/usr/bin/env bash

echo "Submitting job..."

JOB_TIMESTAMP=$(/bin/date +%s%N)
JOB_PID=$$

printenv > /tmp/stdout_job_$JOB_TIMESTAMP

export TOSCA_JOB_ID="${JOB_PID}_${JOB_TIMESTAMP}"

echo "Job $TOSCA_JOB_ID submitted"

