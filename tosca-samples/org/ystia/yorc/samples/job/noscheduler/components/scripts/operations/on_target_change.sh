#!/usr/bin/env bash

TOSCA_JOB_STATUS="COMPLETED"
COMMAND_STDOUT=""
COMMAND_STDERR=""

echo "Target change call for ${TARGET_INSTANCE}"

printenv | grep -e  ^DISPLAY_COMMAND > /tmp/result_${DEPLOYMENT_ID}_${SOURCE_INSTANCE}_${TARGET_INSTANCE}

