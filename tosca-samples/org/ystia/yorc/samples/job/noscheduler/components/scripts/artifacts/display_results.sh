#!/usr/bin/env bash

for f in/tmp/result_${DEPLOYMENT_ID}_${INSTANCE}_*; do

    ## Check if the glob gets expanded to existing files.
    ## If not, f here will be exactly the pattern above
    ## and the exists test will evaluate to false.
    if [ -e "$f" ]; then
       echo "Found file $f"
    fi
done