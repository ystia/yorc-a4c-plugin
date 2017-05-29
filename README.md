# Alien4Cloud Janus Plugin

[Build Status] (http://129.184.11.224/view/Janus%20A4C%20Plugin/)

Janus plugin allows users to deploy their application in an HPC environment.
This means that we allocate nodes with Slurm, then deploy component (docker image, for example) on the allocated node.


## Requirements

[Alien4Cloud version 1.3.x] (http://alien4cloud.github.io/#/documentation/1.3.0/index.html)


## Installation
### Alien4Cloud
* Download: [alien4Cloud v1.3 Download button](https://alien4cloud.github.io/)
* Run on Unix:
alien4cloud.sh
* Run on Windows :
Create a .bat file with :
java -Dhttps.proxyHost=193.56.47.20 -Dhttps.proxyPort=8080 -server -showversion -XX:+AggressiveOpts -Xms512m -Xmx2g -XX:MaxPermSize=512m -cp alien4cloud-ui-1.2.0-SM3-SNAPSHOT.war org.springframework.boot.loader.WarLauncher alien4cloud-ui-1.2.0-SM3-SNAPSHOT.war
Change the proxy based on your needs.
* Visit [http://localhost:8088](http://localhost:8088)
login : admin admin

### Plugin Janus
In the folder of our plugin : alien4cloud-janus-plugin in the folder target we can find the zipped folder alien4cloud-janus-plugin-<version>
Then go to administration tab -> plugins and here drag and drop your compressed folder.


## Quick Start
With this plugin we have a class called JanusPaaSProvider which contains the method doDeploy.
In this method we can have access to the Topology bean.
