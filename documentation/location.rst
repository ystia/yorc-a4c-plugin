..
   Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   ---

Configure a Yorc Orchestrator and a Location
============================================

Now we must define a location (where we will actually deploy applications). In Alien4Cloud every location is managed by an orchestrator.

Configure a Yorc Orchestrator
------------------------------

To create an orchestrator, go to |AdminBtn| and in the |OrchBtn| sub-menu. Create an orchestrator named ``Yorc`` with the following named plugin:
  * Yorc Orchestrator Factory : |version|

At this moment your orchestrator is created but not enabled. Click on your orchestrator to see the information page, and then
click on the configuration menu icon |OrchConfigBtn|.

In the Driver configuration part, add the URL of your Yorc server (should respect the format: ``http://yorc-ip:8800``) and return to the previous page to enable your orchestrator.

If Yorc is secured (ssl enabled):
  * the yorc URL should use the ``https`` protocol
  * the CA authority used to sign the Yorc certificates should be imported in the Java truststore ; otherwise, check ``insecureTL``


Configure an OpenStack Location
-------------------------------

Once your orchestrator is created and enabled, go to the locations page by clicking on |OrchLocBtn|

Create a new location clicking on |OrchLocNewBtn| and provide a location name. Select ``OpenStack`` in the infrastructure type drop-down.

The details page of your location should appear.

Go to |OrchLocODRBtn| and add the following resources:

  * yorc.nodes.openstack.PublicNetwork
  * yorc.nodes.openstack.Compute

Click on the network and set ``floating_network_name`` to the name of your OpenStack public network for the tenant where the Yorc instance
is deployed.

.. image:: _static/img/orchestrator-loc-conf-net.png
   :alt: Network configuration
   :align: center


Click on the compute and set the ``image`` to the id of your image in OpenStack (in order to use our samples in next sections, please use
an Ubuntu 14.04+ or Centos 7.2+ image), the ``flavor`` to ``3`` (medium for a default OpenStack config).

Set ``key_pair`` to the OpenStack keypair that correspond to the private key that you stored under ``~/.ssh/yorc.pem`` during your Yorc server setup.

Finally, in the ``endpoint`` capability of the Compute, open the ``credentials`` complex type and set the ``user`` to a user available in your image (generally ``ubuntu``
for Ubuntu cloud images).
This user will be used to connect to this on-demand compute resource once created, and to deploy applications on it (while the user used to create this on-demand resource is defined in the Yorc server configuration).

.. image:: _static/img/orchestrator-loc-conf-compute.png
   :alt: Compute Node configuration
   :align: center


Configure a Slurm Location
--------------------------

Go to the locations page by clicking on |OrchLocBtn|

Create a new location clicking on |OrchLocNewBtn| and provide a location name. Select ``Slurm`` in the infrastructure type drop-down.

The details page of your location should appear.

Go to |OrchLocODRBtn| and add the following resource:

  * yorc.nodes.slurm.Compute

Click on the compute, the following details should appear and show the endpoint ``credentials`` must be edited:

.. image:: _static/img/slurm-compute.png
   :alt: Compute Node configuration
   :align: center

Edit ``credentials`` and specify a user that will be used to connect to this on-demand compute resource once created,
and to deploy applications on it (while the user used to create this on-demand resource is defined in the Yorc server configuration):

.. image:: _static/img/slurm-credentials.png
   :alt: Compute Node credentials
   :align: center

You could define here as well either a password, provided as a ``token`` parameter value (``token_type`` being set to ``password``),
or a private key by editing the ``keys`` parameter and adding a new key ``0`` with a value being the path to a private key, as below :

.. image:: _static/img/slurm-creds-key.png
   :alt: Compute Node credentials key
   :align: center

If no password or private key is defined, the orchestrator will attempt to use a key ``~/.ssh/yorc.pem`` that should have been defined during your Yorc server setup.

Configure a Hosts Pool Location
-------------------------------

Go to the locations page by clicking on |OrchLocBtn|

Create a new location clicking on |OrchLocNewBtn| and provide a location name. Select ``HostsPool`` in the infrastructure type drop-down.

The details page of your location should appear.

Go to |OrchLocODRBtn| and add the following resource:

  * yorc.nodes.hostspool.Compute

Click on the compute, the following details should appear:

.. image:: _static/img/hosts-pool-compute.png
   :alt: Compute Node configuration
   :align: center

You can select the property ``shareable`` if you want to make this compute node shareable, so that different deployments could use this same resource.

Credentials don't have to be defined here. For hosts in a Hosts Pool, credentials are defined in the Yorc server configuration.

Configure an AWS Location
--------------------------

Go to the locations page by clicking on |OrchLocBtn|

Create a new location clicking on |OrchLocNewBtn| and provide a location name. Select ``AWS`` in the infrastructure type drop-down.

The details page of your location should appear.

Go to |OrchLocODRBtn| and add the following resources:

  * yorc.nodes.aws.PublicNetwork
  * yorc.nodes.aws.Compute

Click on the compute, the following details should appear:

.. image:: _static/img/aws-compute-on-demand.png
   :alt: Compute configuration
   :align: center

Edit mandatory parameters AWS ``image_id``, ``instance_type``, ``security_groups`` and ``key_name`` to provide the name of a key pair already known from AWS.

Edit ``credentials`` to provide a user name.
This user will be used to connect to this on-demand compute resource once created, and to deploy applications on it (while the user used to create this on-demand resource is defined in the Yorc server configuration).

.. |AdminBtn| image:: _static/img/administration-btn.png
              :alt: administration


.. |OrchBtn| image:: _static/img/orchestrator-menu-btn.png
             :alt: orchestrator


.. |OrchConfigBtn| image:: _static/img/orchestrator-config-btn.png
                   :alt: orchestrator configuration


.. |OrchLocBtn| image:: _static/img/orchestrator-location-btn.png
                :alt: orchestrator location

.. |OrchLocODRBtn| image:: _static/img/on-demand-ressource-tab.png
                   :alt: on-demand resources


.. |OrchLocNewBtn| image:: _static/img/new-location.png
                   :alt: new location

