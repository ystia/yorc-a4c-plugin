#!/usr/bin/env bash

TOSCA_JOB_STATUS="COMPLETED"

# TOSCA_JOB_ID has format <Jog ID>_<Timestamp>
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
    if [ -f /var/run/stdout_job_$JOB_TIMESTAMP ]; then
      outContent=`/usr/bin/cat /var/run/stdout_job_$JOB_TIMESTAMP`
      if [ -n "$outContent" ]; then
        echo "Job output: $outContent"
      fi
    fi

    # Considering the job failed if there are error logs
    if [ -f /var/run/stderr_job_$JOB_TIMESTAMP ]; then
      errContent=`/usr/bin/cat /var/run/stderr_job_$JOB_TIMESTAMP`
      if [ -n "$errContent" ]; then
        TOSCA_JOB_STATUS="FAILED"
        echo "Job errors: $errContent"
      fi
    fi

    # Cleanup
    /bin/rm -f /var/run/stdout_job_$JOB_TIMESTAMP
    /bin/rm -f /var/run/stderr_job_$JOB_TIMESTAMP
  fi
fi

echo "Job $TOSCA_JOB_ID status: $TOSCA_JOB_STATUS"
export TOSCA_JOB_STATUS

