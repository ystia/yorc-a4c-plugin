#!/usr/bin/env bash

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
rootDir=$(readlink -f "${scriptDir}/../../../..")

if [[ "${TRAVIS}" != "true" ]] ; then
    echo "This script is designed to publish CI build artifacts"
    exit 0
fi

if [[ "${DISABLE_ARTIFACTORY}" == "true" ]] ; then
    echo "Skipping Artifactory publication"
    exit 0
fi

if [[ "${TRAVIS_PULL_REQUEST}" != "false" ]] && [[ -z "${ARTIFACTORY_API_KEY}" ]] ; then
    echo "Building an external pull request, artifactory publication is disabled"
    exit 0
fi

if [[ -n "${TRAVIS_TAG}" ]] ; then
    deploy_path="yorc-a4c-plugin-product-ystia-dist/ystia/yorc-a4c-plugin/dist/${TRAVIS_TAG}/{1}"
elif [[ "${TRAVIS_PULL_REQUEST}" != "false" ]]; then
    deploy_path="yorc-a4c-plugin-bin-dev-local/ystia/yorc-a4c-plugin/dist/PR-${TRAVIS_PULL_REQUEST}/{1}"
else
    deploy_path="yorc-a4c-plugin-bin-dev-local/ystia/yorc-a4c-plugin/dist/${TRAVIS_BRANCH}/{1}"
fi

curl -fL https://getcli.jfrog.io | sh

build_name="yorc-a4c-plugin-travis-ci"

./jfrog rt c --apikey="${ARTIFACTORY_API_KEY}" --user=travis --url=https://ystia.jfrog.io/ystia ystia
./jfrog rt u --build-name="${build_name}" --build-number="${TRAVIS_BUILD_NUMBER}" --props="artifactory.licenses=Apache-2.0" --regexp "distribution/target/(alien4cloud-yorc-plugin-distribution-.*.zip)" "${deploy_path}"
./jfrog rt u --build-name="${build_name}" --build-number="${TRAVIS_BUILD_NUMBER}" --props="artifactory.licenses=Apache-2.0" --regexp "alien4cloud-yorc-plugin/target/(alien4cloud-yorc-plugin-.*.zip)" "${deploy_path}"
# Do not publish environment variables as it may expose some secrets
#./jfrog rt bce "${build_name}" "${TRAVIS_BUILD_NUMBER}"
./jfrog rt bag "${build_name}" "${TRAVIS_BUILD_NUMBER}" "${rootDir}"
./jfrog rt bp "${build_name}" "${TRAVIS_BUILD_NUMBER}"