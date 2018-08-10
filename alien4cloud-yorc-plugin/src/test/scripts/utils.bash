#!/bin/bash

#
# utils - Library of functions used to perform Alien4Cloud REST calls
#

#
# a4c_login - login on Alien4Cloud anf store cookies
# Expects 4 parameters:
# - URL of Alien4Cloud
# - user name
# - password
# - file where to store cookies
a4c_login() {
   if [ $# != 4 ]
   then
       echo "Usage:"
       echo "a4c_login.bash <URL> <user> <password> <file where to store cookies>"
       exit 1
   fi

    curl --data "username=$2&password=$3&submit=Login"  \
         --url  $1/login \
         --dump-header headers \
	     --silent \
         --cookie-jar $4

}

# Get the value of a given key in a JSON string
# Expects 2 parameters:
# - key
# - JSON string
getJsonval() {
	jsonKey=$1
	jsonContent=$2
    temp=`echo "$jsonContent" | awk -F"[{,:}]" '{for(i=1;i<=NF;i++){if($i~/\042'$jsonKey'\042/){print $(i+1)}}}' | tr -d '"' | sed -n 1p`
    echo $temp
}