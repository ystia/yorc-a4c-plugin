#!/usr/bin/env bash 
#
# Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -x
set -e
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd ${scriptDir}

python -c "import semantic_version" > /dev/null 2>&1 || {
    echo -e "Python library semantic_version is required.\nPlease install it using:\n\tpip install semantic_version" >&2
    exit 1
}

dryRun=false
version=
PUSH_URL=
while getopts ":dv:p:" opt; do
  case $opt in
    v)
      version=${OPTARG}
      ;;
    d)
      dryRun=true
      ;;
    p)
      PUSH_URL=${OPTARG}
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done

if [[ -z "${version}" ]]; then
    echo "Parameter -v is required to indicate the version to build and tag" >&2
    exit 1
fi

if [[ "$(python -c "import semantic_version; print semantic_version.validate('${version}')" )" != "True" ]]; then
    echo "Parameter -v should be a semver 2.0 compatible version (http://semver.org/)" >&2
    exit 1
fi

# read version
read -r major minor patch prerelease build <<< $(python -c "import semantic_version; v = semantic_version.Version('${version}'); print v.major, v.minor, v.patch, '.'.join(v.prerelease), '.'.join(v.build);")

# Detect correct supporting branch
branch=$(git branch --list -r "*/release/${major}.${minor}")
if [[ -z "${branch}" ]]; then
    branch="develop"
fi
branch=$(echo ${branch} | sed -e "s@^.*/\(release/.*\)@\1@")
echo "Switching to branch ${branch}..."
releaseBranch=${branch}
git checkout ${branch} 


currentVersion=$(python -c "import xml.etree.ElementTree as ET; print(ET.parse(open('pom.xml')).getroot().find('{http://maven.apache.org/POM/4.0.0}version').text)")
checkVers=$(echo ${currentVersion} | sed -e "s/-SNAPSHOT/-0/")
if [[ "True" != "$(python -c "import semantic_version; print  semantic_version.Version('${version}') >= semantic_version.Version('${checkVers}')" )" ]]; then
    echo "Can't release version ${version} on top of branch ${branch} as its current version is ${currentVersion}" >&2
    exit 1
fi

# Check branch tags
branchTag=$(git describe --abbrev=0 --tags ${branch}) || {
    branchTag="v0.0.0"
}
branchTag=$(echo $branchTag | sed -e 's/^janus-a4c-plugin-\(.*\)$/\1/' -e 's/^v\(.*\)$/\1/')

if [[ "True" != "$(python -c "import semantic_version; print  semantic_version.Version('${version}') > semantic_version.Version('${branchTag}')" )" ]]; then
    echo "Can't release version ${version} on top of branch ${branch} as it contains a newer tag: v${branchTag}" >&2
    exit 1
fi

if [[ "develop" == "${branch}" ]] && [[ -z "${prerelease}" ]]; then
    # create release branch
    releaseBranch="release/${major}.${minor}"
    git checkout -b "${releaseBranch}"
fi

# Now checks are passed then tag, build, release and cleanup :)
# Update changelog Release date
if [[ -e CHANGELOG.md ]]; then
    sed -i -e "s/^## UNRELEASED.*$/## ${version} ($(LC_ALL=C date +'%B %d, %Y'))/g" CHANGELOG.md
    git commit -m "Update changelog for release ${version}" CHANGELOG.md
fi

if [[ -n "${prerelease}" ]]; then 
    # in prerelease revert to version minus prerelease plus -SNAPSHOT
    nextDevelopmentVersion="${major}.${minor}.${patch}-SNAPSHOT"
else
    nextDevelopmentVersion=$(python -c "import semantic_version; v=semantic_version.Version('${version}'); print v.next_patch()" )
    nextDevelopmentVersion="${nextDevelopmentVersion}-SNAPSHOT"
fi

if [[ -n "$PUSH_URL" ]]; then
    mvnOpts="$(echo $PUSH_URL | sed -e "s~.*://\([^:@]*\):\([^@]*\)@.*~-Dusername=\1 -Dpassword=\2~g")"
fi

if [ "${dryRun}" = false ] ; then
    mvnOpts="${mvnOpts} -DpushChanges=true"
else 
    mvnOpts="${mvnOpts} -DpushChanges=false"
fi
####################################################
# Make our build
####################################################
echo "Building version v${version}"
set +x
mvn release:clean release:prepare ${mvnOpts} --batch-mode -Dtag=v${version} -DreleaseVersion=${version} -DdevelopmentVersion=${nextDevelopmentVersion}
echo "Tag done. Publishing release..."
#if [ "${dryRun}" = true ] ; then
#    mvnOpts="${mvnOpts} -DdryRun=true"
#fi
# mvn release:perform --batch-mode ${mvnOpts}
set -x

# Update changelog for future versions
if [[ -e CHANGELOG.md ]]; then
    sed -i -e "2a## UNRELEASED\n" CHANGELOG.md
    git commit -m "Update changelog for future release" CHANGELOG.md
fi

if [[ "develop" == "${branch}" ]] && [[ -z "${prerelease}" ]]; then
    # merge back to develop and update version
    git checkout develop
    git merge --no-ff "v${version}" -m "merging latest tag v${version} into develop"
    # Update changelog for future versions
    if [[ -e CHANGELOG.md ]]; then
        sed -i -e "2a## UNRELEASED\n" CHANGELOG.md
        git commit -m "Update changelog for future release" CHANGELOG.md
    fi
    nextDevelopmentVersion=$(python -c "import semantic_version; v=semantic_version.Version('${version}'); print v.next_minor()" )
    nextDevelopmentVersion="${nextDevelopmentVersion}-SNAPSHOT"
    mvn --batch-mode release:update-versions -DdevelopmentVersion=${nextDevelopmentVersion}
    find . -name pom.xml | grep -v "/target/" | xargs git commit -m "Prepare next development version" 
fi

if [[ -z "${prerelease}" ]]; then
    # Merge on master only final version
    masterTag=$(git describe --abbrev=0 --tags master) || {
        masterTag="v0.0.0"
    }
    masterTag=$(echo ${masterTag} | sed -e 's/^janus-a4c-plugin-\(.*\)$/\1/' -e 's/^v\(.*\)$/\1/')

    if [[ "True" == "$(python -c "import semantic_version; print  semantic_version.Version('${version}') > semantic_version.Version('${masterTag}')" )" ]]; then
        # We should merge the tag to master as it is our highest release
        git checkout master
        git merge --no-ff "v${version}" -X theirs -m "merging latest tag v${version} into master" || {
                git merge --abort || true
                git reset --hard "v${version}"
        }
    fi
fi


# Push changes
if [ "${dryRun}" = false ] ; then
    set +x
    git push ${PUSH_URL} --all
    git push ${PUSH_URL} --tags
fi

