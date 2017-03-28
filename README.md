# Alien4Cloud Janus Plugin

[![Build Status](http://10.197.135.134:8080/job/alien4cloud-janus-plugin/badge/icon)](http://10.197.135.134:8080/job/alien4cloud-janus-plugin/)

Janus plugin allows users to deploy their application in an HPC environment.
This means that we allocate nodes with Slurm, then deploy component (docker image, for example) on the allocated node.


## Requirements

- Alien4Cloud version 1.3.x
- http://alien4cloud.github.io/#/documentation/1.1.0/devops_guide/


## Installation
### Alien4Cloud
* Download: [alien4Cloud not the latest build v1.2](http://fastconnect.org/maven/service/local/artifact/maven/redirect?r=opensource&g=alien4cloud&a=alien4cloud-dist&v=1.2.0-RC1&p=tar.gz&c=dist)
* Run :
* Unix:
alien4cloud.sh
* Windows :
Create a .bat file with :
java -Dhttps.proxyHost=193.56.47.20 -Dhttps.proxyPort=8080 -server -showversion -XX:+AggressiveOpts -Xms512m -Xmx2g -XX:MaxPermSize=512m -cp alien4cloud-ui-1.2.0-SM3-SNAPSHOT.war org.springframework.boot.loader.WarLauncher alien4cloud-ui-1.2.0-SM3-SNAPSHOT.war
Change the proxy based on your needs.
* Visit [http://localhost:8088](http://localhost:8088)
login : admin admin

### Plugin Janus
In the folder of our plugin : alien4cloud-janus-plugin in the folder target we can find the zipped folder alien4cloud-janus-plugin-<version>
Then go to administration tab -> plugins and here drag and drop your compressed folder.


## Quick Start
With this plugin we have a class called JanusPaaSProvider which contains the method doDeploy. In this method we can have access to the Topology bean.
In the doDeploy in order to deploy the topology, we first use the function workflowReader to create a list of steps, stored in the proper order.
With this list, we use the function workflowPlayer to parse this list of steps (bean workflowStep) and execute scripts which follow the step.
