# Yorc Alien4Cloud Plugin Changelog

## UNRELEASED

## 3.2.0-M5 (April 19, 2019)

### FEATURES

* Implement an anti-affinity placement policy for Openstack ([GH-84](https://github.com/ystia/yorc/issues/84))
* Monitor deployed services liveness ([GH-104](https://github.com/ystia/yorc/issues/104))

### BUG FIXES

* Scale Down operation never ending with compute instance final status 'Initial' ([GH-117](https://github.com/ystia/yorc-a4c-plugin/issues/117))


## 3.2.0-M4 (March 29, 2019)

### FEATURES

* Deployment update: support the ability to add/remove workflows with Yorc Premium version ([GH-112](https://github.com/ystia/yorc-a4c-plugin/issues/112))
* Yorc support of kubernetes PersistentVolumeClaim ([GH-209](https://github.com/ystia/yorc/issues/209))

### BUG FIXES

* Application undeployment seen in progress until timeout of 30 minutes occurs ([GH-110](https://github.com/ystia/yorc-a4c-plugin/issues/110))


## 3.2.0-M3 (March 11, 2019)

### FEATURES

* Yorc supports Slurm Accounting ([GH-280](https://github.com/ystia/yorc/issues/280))
* Yorc supports Slurm reservation ([GH-132](https://github.com/ystia/yorc/issues/132))

## 3.2.0-M2 (February 15, 2019)

### DEPENDENCIES

* Upgrade to Alien4Cloud 2.1.1

### ENHANCEMENTS

* Add SSL configuration parameters to connect to a secure Yorc Server ([GH-82](https://github.com/ystia/yorc-a4c-plugin/issues/82))
* Publish value change event for instance attributes ([GH-222](https://github.com/ystia/yorc/issues/222))
* Slurm user credentials can be defined as slurm deployment topology properties, as an alternative to yorc configuration properties ([GH-281](https://github.com/ystia/yorc/issues/281))

### BUG FIXES

* Deploying applications simultaneously can fail on invalid zip error ([GH-45](https://github.com/ystia/yorc-a4c-plugin/issues/45))
* Uninstall workflow is not correct for Topology involving BlockStorage node ([GH-90](https://github.com/ystia/yorc-a4c-plugin/issues/90))
* Yorc failure at undeployment leaves an app unpurged on Yorc server while undeployed in Alien4Cloud ([GH-95](https://github.com/ystia/yorc-a4c-plugin/issues/95))

## 3.2.0-M1 (January 28, 2019)

### BUG FIXES

* Can't connect to Yorc in secure mode  ([GH-81](https://github.com/ystia/yorc-a4c-plugin/issues/81))
* Deployment status inconsistency when restarting Alien4Cloud and an application finishes to deploy  ([GH-77](https://github.com/ystia/yorc-a4c-plugin/issues/77))
* Uninstall workflow is not correct for Topology involving BlockStorage node ([GH-90](https://github.com/ystia/yorc-a4c-plugin/issues/90))

## 3.1.0 (December 20, 2018)

### BUG FIXES

* Vision sample topology upload fails on component version issue ([GH-78](https://github.com/ystia/yorc-a4c-plugin/issues/78))

## 3.1.0-RC2 (December 18, 2018)

### DEPENDENCIES

* Technical update to use Alien4Cloud 2.1.0 final version
* Updated Slurm and Kubernetes types to final version (respectively 1.1.0 and 2.0.0)

## 3.1.0-RC1 (December 17, 2018)

### ENHANCEMENTS

* Support Jobs lifecycle enhancements (new operations `submit`, `run`, `cancel`) ([GH-196](https://github.com/ystia/yorc/issues/196))
* Generate Alien 2.1-compatible events ([GH-148](https://github.com/ystia/yorc/issues/148))

### BUG FIXES

* Even with a wrong yorc url in orchestrator configuration, it displays "connected" when enabled ([GH-72](https://github.com/ystia/yorc-a4c-plugin/issues/72))

## 3.1.0-M7 (December 07, 2018)

## 3.1.0-M6 (November 16, 2018)

### FEATURES

* Support GCE virtual private networks (VPC) ([GH-80](https://github.com/ystia/yorc/issues/80))
* Support Kubernetes Jobs. ([GH-67](https://github.com/ystia/yorc-a4c-plugin/issues/67))

### ENHANCEMENTS

* Take advantage of Alien4Cloud meta-properties to specify a namespace in which to deploy Kubernetes resources ([GH-76](https://github.com/ystia/yorc/issues/76))

## 3.1.0-M5 (October 26, 2018)

### ENHANCEMENTS

* Enable scaling of Kubernetes deployments ([GH-77](https://github.com/ystia/yorc/issues/77))

### BUG FIXES

* Node Instance attributes are only resolved when Node state is "started" ([GH-59](https://github.com/ystia/yorc-a4c-plugin/issues/59))

### FEATURES

* Support GCE Block storages. ([GH-82](https://github.com/ystia/yorc/issues/81))

## 3.1.0-M4 (October 08, 2018)

### DEPENDENCIES

* Upgrade to Alien4Cloud 2.1 ([GH-50](https://github.com/ystia/yorc-a4c-plugin/issues/50))

### FEATURES

* Support GCE Public IPs. ([GH-82](https://github.com/ystia/yorc/issues/82))

### IMPROVEMENTS

* Make the run step of a Job execution asynchronous not to block a worker during the duration of the job. ([GH-85](https://github.com/ystia/yorc/issues/85))

## 3.1.0-M3 (September 14, 2018)

## 3.1.0-M2 (August 24, 2018)

### BUG FIXES

* When an artifact references a folder its content is not part of the resulting CSAR sent to Yorc (GH-43)
* When an orchestrator has been disabled, the Yorc A4C plugin is still trying to listen log events (GH-34)
* On TOSCA types generation do not generate a artifact if its mandatory file parameter is empty (GH-15)

## 3.1.0-M1 (August 6, 2018)

### FEATURES

* Support of applications secrets in Yorc engine makes it usable within Alien4Cloud (ystia/yorc#134)

## 3.0.0 (July 11, 2018)

### Naming & Open Source community

Yorc 3.0.0 is the first major version since we open-sourced the formerly known Janus project. Previous versions have been made available on GitHub.

We are still shifting some of our tooling like road maps and backlogs publicly available tools. The idea is to make project management clear and to open Yorc to external contributions.

### Shifting to Alien4Cloud 2.0

Alien4Cloud released recently a fantastic major release with new features leveraged by Yorc to deliver a great orchestration solution.

Among many features, the ones we will focus on below are:

* UI redesign: Alien4Cloud 2.0.0 includes various changes in UI in order to make it more consistent and easier to use.
* Topology modifiers: Alien4Cloud 2.0.0 allows to define modifiers that could be executed in various phases prior to the deployment. Those modifiers allow to transform a given TOSCA topology.

### New GCP infrastructure

We are really excited to announce our first support of [Google Cloud Platform](https://cloud.google.com/).

Yorc now natively supports [Google Compute Engine](https://cloud.google.com/compute/) to create compute on demand on GCE.

### New Hosts Pool infrastructure

Yorc 3.0.0 supports a new infrastructure that we called "Hosts Pool". It allows to register generic hosts into Yorc and let Yorc allocate them for deployments. These hosts can be anything, VMs, physical machines, containers, ... whatever as long as we can ssh into them for provisioning. Yorc exposes a REST API and a CLI that allow to manage the hosts pool, making it easy to integrate it with other tools.

For more informations about the Hosts Pool infrastructure, check out [our dedicated documentation](http://yorc.readthedocs.io/en/latest/infrastructures.html#hosts-pool).

### Slurm infrastructure

We made some improvements with our Slurm integration:

* We now support Slurm "features" (which are basically tags on nodes) and "constraints" syntax to allocate nodes. [Examples here](https://wiki.rc.usf.edu/index.php/SLURM_Using_Features_and_Constraints).
* Support of srun and sbatch commands (see Jobs scheduling below)

### Refactoring Kubernetes infrastructure support

In Yorc 2 we made a first experimental integration with Kubernetes. This support and associated TOSCA types are deprecated in Yorc 3.0. Instead we switched to [new TOSCA types defined collectively with Alien4Cloud](http://alien4cloud.github.io/#/documentation/2.0.0/orchestrators/kubernetes/kubernetes_walkthrough.html).

This new integration will allow to build complex Kubernetes topologies.

### Support of Alien4Cloud Services

[Alien4Cloud has a great feature called "Services"](http://alien4cloud.github.io/common/features.html#/documentation/2.0.0/concepts/services.html). It allows both to define part of an application to be exposed as a service so that it can be consumed by other applications, or to register an external service in Alien4Cloud to be exposed and consumed by applications.

This feature allows to build new use cases like cross-infrastructure deployments or shared services among many others.

We are very excited to support it!

### Operations on orchestrator's host

Yet another super interesting feature! Until now TOSCA components handled by Yorc were designed to be hosted on a compute (whatever it was) that means that component's life-cycle scripts were executed on the provisioned compute. This feature allows to design components that will not necessary be hosted on a compute, and if not, life-cycle scripts are executed on the Yorc's host.

This opens a wide range of new use cases. You can for instance implement new computes implementations in pure TOSCA by calling cloud-providers CLI tools or interact with external services

Icing on the cake, for security reasons those executions are by default sand-boxed into containers to protect the host from mistakes and malicious usages.

### Jobs scheduling

This release brings a tech preview support of jobs scheduling. It allows to design workloads made of Jobs that could interact with each other and with other "standard" TOSCA component within an application. We worked hard together with the Alien4Cloud team to extent TOSCA to support Jobs scheduling.

In this release we mainly focused on the integration with Slurm for supporting this feature (but we are also working on Kubernetes for the next release :smile:). Bellow are new supported TOSCA types and implementations:

* SlurmJobs: will lead to issuing a srun command with a given executable file.  
* SlurmBatch: will lead to issuing a sbatch command with a given batch file and associated executables
* Singularity integration: allows to execute a Singularity container instead of an executable file.

### Mutual SSL auth between Alien & Yorc

Alien4Cloud and Yorc can now mutually authenticate themselves with TLS certificates.

### New Logs formating

We constantly try to improve feedback returned to our users about runtime execution. In this release we are publishing logs with more context on the node/instance/operation/interface to which the log relates to.

### Monitoring

Yorc 3.0 brings foundations on applicative monitoring, it allows to monitor compute liveness at a interval defined by the user. When a compute goes down or up we use or events API to notify the user and Alien4Cloud to monitor an application visually within the runtime view.

Our monitoring implementation was designed to be a fault-tolerant service.
