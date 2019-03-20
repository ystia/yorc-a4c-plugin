#!/bin/bash

# Take care to this: https://github.com/sylabs/singularity/issues/2146
set -o pipefail

echo "date debut " > /out/output.txt
echo "`date`" >> /out/output.txt
python3 bin/mnist_deep.py --data_dir /data | tee -a /out/accuracy.out
echo "date fin " >> /out/output.txt
echo "`date`" >> /out/output.txt
