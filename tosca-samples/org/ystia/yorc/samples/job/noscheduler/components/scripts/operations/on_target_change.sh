#!/usr/bin/env bash

echo "Target change call for ${TARGET_INSTANCE}"

outputfile=/tmp/result_${DEPLOYMENT_ID}_${SOURCE_INSTANCE}_${TARGET_INSTANCE}

echo "DISPLAY_COMMAND_SPAWNED=\"${DISPLAY_COMMAND_SPAWNED}\"" > $outputfile
echo "DISPLAY_COMMAND_STDOUT=\"${DISPLAY_COMMAND_STDOUT}\"" >> $outputfile
echo "DISPLAY_COMMAND_STDERR=\"${DISPLAY_COMMAND_STDERR}\"" >> $outputfile
