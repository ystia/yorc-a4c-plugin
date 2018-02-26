# Contributing to the Ystia Orchestrator plugin for Alien4Cloud project

**First off**, thanks for taking time to contribute!

The following is a set of guidelines for contributing to the Yorc plugin for alien4cloud.
Feel free to provide feedback about it in an issue or pull request.

Don't be afraid to contribute, if something is unclear then just ask or submit the issue or pull request
anyways. The worst that can happen is that you'll be politely asked to change something.

## How to build Yorc plugin from source

Java JDK 1.8+ and maven 3+ are required to build the project.

Sphinx and make are required to build the documentation.

Here is how to install and setup the Yorc plugin project:

    sudo apt-get install build-essential git curl
    # Or
    sudo yum install build-essential git curl

    # Install Go and set GOPATH
    git clone https://github.com/ystia/yorc-a4c-plugin.git
    cd yorc-a4c-plugin

    # Build
    mvn clean install

## How to contribute

You can contribute to the Yorc project in several ways. All of them are welcome.

* Report a documentation issue
* Report a bug
* Report an improvement request
* Propose a PR that fixes one of the above, we will try to tag `good_first_pr` issues that are a good starting point for contributing

### Report an issue

Use [Github issues](https://github.com/ystia/yorc-a4c-plugin/issues) to report issues.
Please try to answer most of the questions asked in the issue template.

### Propose a Pull Request

**Working on your first Pull Request?** You can learn how from this *free* series [How to Contribute to an Open Source Project on GitHub](https://egghead.io/series/how-to-contribute-to-an-open-source-project-on-github)

Use [Github pull requests](https://github.com/ystia/yorc-a4c-plugin/pulls) to propose a PR.
Please try to answer most of the questions asked in the pull request template.

## Coding Style

<!--
TODO: sounds a good idea to include a checkstyle for this project
-->

### License headers

You should apply the license header to the source files. This is enforced by the maven build.

To automatically apply the license header use `mvn com.mycila:license-maven-plugin:format`
