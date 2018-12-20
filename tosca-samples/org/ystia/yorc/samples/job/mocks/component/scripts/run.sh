#!/usr/bin/env bash

echo "Checking status for job ${TOSCA_JOB_ID}"
echo "First, let sleep for ${DELAY} seconds..."
sleep ${DELAY}

TOSCA_JOB_STATUS="COMPLETED"

if [[ "${FAILURE}" == "true" ]] ; then
    TOSCA_JOB_STATUS="FAILED"
fi

if [[ "${RANDOM_STATUS}" == "true" ]] ; then 
    mod=3
    if [[ "${FAILURE}" == "true" ]] ; then
        mod=4
    fi
    case $(( ${RANDOM} % ${mod} )) in
    2)
        TOSCA_JOB_STATUS="COMPLETED"
        ;;
    3)
        # May happen only if mod=4
        TOSCA_JOB_STATUS="FAILED"
        ;;
    *)
        TOSCA_JOB_STATUS="RUNNING"
        ;;
    esac
fi

echo "Computed job status is: ${TOSCA_JOB_STATUS}"

export TOSCA_JOB_STATUS