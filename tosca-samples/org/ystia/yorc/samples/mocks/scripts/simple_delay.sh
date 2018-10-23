#!/usr/bin/env bash

# Set DELAY to 0 by default
: "${DELAY:=0}"

sleep ${DELAY}

echo "Done!"

export DATE=$(date --rfc-3339=ns)
