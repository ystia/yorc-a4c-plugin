#!/usr/bin/env bash

TOSCA_JOB_STATUS="COMPLETED"
COMMAND_STDOUT=""
COMMAND_STDERR=""

# TOSCA_JOB_ID has format <Job ID>_<Timestamp>
IFS='_' read -r -a array <<< "${TOSCA_JOB_ID}"
res=${#array[@]}
if [ "$res" -ne "2" ];then
  echo "Wrong format for TOSCA_JOB_ID ${TOSCA_JOB_ID}"
  TOSCA_JOB_STATUS="FAILED"
else
  JOB_ID="${array[0]}"
  JOB_TIMESTAMP="${array[1]}"
  if /usr/bin/ps -p $JOB_ID > /dev/null
  then
    TOSCA_JOB_STATUS="RUNNING"
  else
    # Job done.
    # Retrieving outputs
    if [ -f /tmp/stdout_job_$JOB_TIMESTAMP ]; then
      COMMAND_STDOUT=`/usr/bin/cat /tmp/stdout_job_$JOB_TIMESTAMP`
      if [ -n "$COMMAND_STDOUT" ]; then
        echo "Job output: $COMMAND_STDOUT"
      fi
    fi

    # Considering the job failed if there are error logs
    if [ -f /tmp/stderr_job_$JOB_TIMESTAMP ]; then
      COMMAND_STDERR=`/usr/bin/cat /tmp/stderr_job_$JOB_TIMESTAMP`
      if [ -n "$COMMAND_STDERR" ]; then
        TOSCA_JOB_STATUS="FAILED"
        echo "Job errors: $COMMAND_STDERR"
      fi
    fi

    # Cleanup
    /bin/rm -f /tmp/stdout_job_$JOB_TIMESTAMP
    /bin/rm -f /tmp/stderr_job_$JOB_TIMESTAMP
  fi
fi

echo "Job $TOSCA_JOB_ID status: $TOSCA_JOB_STATUS"
export TOSCA_JOB_STATUS
export COMMAND_STDOUT
export COMMAND_STDERR


