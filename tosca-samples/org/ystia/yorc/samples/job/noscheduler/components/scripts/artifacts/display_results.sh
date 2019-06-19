#!/usr/bin/env bash

# Get variables stored by on_target_change.sh operation
INSTANCE_PREFIX=/tmp/result_${DEPLOYMENT_ID}_${INSTANCE}_
/usr/bin/sleep 1
for f in ${INSTANCE_PREFIX}*; do

    if [ -e "$f" ]; then
        TARGET_INSTANCE_NAME=${f#"$INSTANCE_PREFIX"}
        echo ""
        echo "Results from target $TARGET_INSTANCE_NAME:"
        . $f
        echo "  command: $DISPLAY_COMMAND_SPAWNED"
        echo "  output : $DISPLAY_COMMAND_STDOUT"
        echo "  error  : $DISPLAY_COMMAND_STDERR"
        /bin/rm -f $f
    fi
done
