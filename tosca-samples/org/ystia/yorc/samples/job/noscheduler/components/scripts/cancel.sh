#!/usr/bin/env bash


# TOSCA_JOB_ID has format <Jog ID>_<Timestamp>
IFS='_' read -r -a array <<< "${TOSCA_JOB_ID}"
res=${#array[@]}
if [ "$res" -ne "2" ];then
  echo "Wrong format for TOSCA_JOB_ID ${TOSCA_JOB_ID}"
else
  JOB_ID="${array[0]}"
  JOB_TIMESTAMP="${array[1]}"
  if /usr/bin/ps -p $JOB_ID > /dev/null
  then
    /usr/bin/kill -TERM $JOB_ID
    echo "Job $JOB_ID canceled"
  else
    echo "Job $JOB_ID already completed"
  fi

  # Job done.
  # Retrieving outputs
  if [ -f /tmp/stdout_job_$JOB_TIMESTAMP ]; then
    outContent=`/usr/bin/cat  /tmp/stdout_job_$JOB_TIMESTAMP`
    if [ -n "$outContent" ]; then
      echo "Job output: $outContent"
    fi
  fi

  if [ -f /tmp/stderr_job_$JOB_TIMESTAMP ]; then
    errContent=`/usr/bin/cat /tmp/stderr_job_$JOB_TIMESTAMP`
    if [ -n "$errContent" ]; then
      echo "Job errors: $errContent"
    fi
  fi
  # Cleanup
  /bin/rm -f /tmp/stdout_job_$JOB_TIMESTAMP
  /bin/rm -f /tmp/stderr_job_$JOB_TIMESTAMP
fi
