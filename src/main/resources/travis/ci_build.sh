#!/usr/bin/env bash
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
rootDir=$(readlink -f "${scriptDir}/../../../..")

cd "${rootDir}"

sudo cp fastconnect.org.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates

export M2_HOME=/usr/local/maven

set -e

mvn_args="clean install -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

if [[ "${TRAVIS_PULL_REQUEST}" != "false" ]] && [[ -z "${ARTIFACTORY_API_KEY}" ]] ; then
    echo "Building an external pull request, artifactory publication is disabled"
    echo; echo
    mvn ${mvn_args}
elif [[ "${DISABLE_ARTIFACTORY}" == "true" ]] ; then
    echo "Skipping Artifactory publication"
    mvn ${mvn_args}
else 
    ./jfrog rt c --apikey="${ARTIFACTORY_API_KEY}" --user=travis --url=https://ystia.jfrog.io/ystia ystia
    ./jfrog rt mvn "${mvn_args}" src/main/resources/artifactory/maven_config_file.yaml --build-name="yorc-a4c-plugin-mvn-travis-ci" --build-number="${TRAVIS_BUILD_NUMBER}"
    ./jfrog rt bag "yorc-a4c-plugin-mvn-travis-ci" "${TRAVIS_BUILD_NUMBER}" .
    ./jfrog rt bp "yorc-a4c-plugin-mvn-travis-ci" "${TRAVIS_BUILD_NUMBER}"
fi