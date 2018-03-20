#!/usr/bin/env bin

>&2 echo "Failure is the key to success; each mistake teaches us something. Morihei Ueshiba"

echo "Success consists of going from failure to failure without loss of enthusiasm. Winston Churchill"

if [[ "${SHOULD_FAIL}" == "true" ]]; then
    exit 1
fi
