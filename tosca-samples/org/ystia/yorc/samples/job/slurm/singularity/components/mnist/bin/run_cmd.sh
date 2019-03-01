#!/bin/bash
set -o pipefail

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "date debut " > /out/output.txt
echo "`date`" >> /out/output.txt
python3 ${script_dir}/mnist_deep.py --data_dir /data | tee -a /out/accuracy.out
echo "date fin " >> /out/output.txt
echo "`date`" >> /out/output.txt
