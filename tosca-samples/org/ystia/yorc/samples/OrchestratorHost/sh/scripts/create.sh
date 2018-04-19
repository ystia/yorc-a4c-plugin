#!/usr/bin/env bash

echo "running create.sh on orchestrator."

echo
echo
echo "path"
pwd

echo
echo

echo "ip config"
hostname -f
ip a


echo
echo

echo "MY_INPUT: ${MY_INPUT}"


export MY_OUTPUT="$(echo "${MY_INPUT}" | rev)"
