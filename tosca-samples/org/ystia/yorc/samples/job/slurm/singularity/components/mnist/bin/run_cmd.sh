#!/bin/bash
set -o pipefail

echo "date debut " > /out/output.txt
echo "`date`" >> /out/output.txt
python3 bin/mnist_deep.py --data_dir /data | tee -a /out/accuracy.out
echo "date fin " >> /out/output.txt
echo "`date`" >> /out/output.txt
