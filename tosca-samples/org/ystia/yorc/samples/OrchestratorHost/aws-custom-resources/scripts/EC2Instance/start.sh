#!/bin/bash -e

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}"

instance_id=$(aws ec2 run-instances --image-id ${IMAGE_ID} --security-group-ids ${SECURITY_GROUP_IDS} --count 1 --instance-type ${INSTANCE_TYPE} --key-name ${KEY_NAME} --query 'Instances[0].InstanceId' --output text)
if [ ! $? -eq 0 ]; then
	exit 1
fi
if [ -z $instance_id ]; then
	echo "no instance_id can be identified" >&2
	exit 1
fi

ip_address=$(aws ec2 describe-instances --instance-ids ${instance_id} --query 'Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddress' --output text)
public_ip_address=$(aws ec2 describe-instances --instance-ids ${instance_id} --query 'Reservations[0].Instances[0].NetworkInterfaces[0].Association.PublicIp' --output text)

export IP_ADDRESS="${ip_address}"
export PUBLIC_IP_ADDRESS="${public_ip_address}"
export INSTANCE_ID="${instance_id}"

availibility_zone=$(aws ec2 describe-instances --instance-ids ${instance_id} --query 'Reservations[0].Instances[0].Placement.AvailabilityZone' --output text)
export AVAILIBILITY_ZONE="${availibility_zone}"

echo 'Waiting for instance to reach the "running" state'
aws ec2 wait instance-running
