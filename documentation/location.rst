Configure a Yorc Orchestrator and an OpenStack Location
========================================================

Now we must define a location (where we will actually deploy applications). In Alien4Cloud every location is managed by an orchestrator.

Configure a Yorc Orchestrator
------------------------------

To create an orchestrator, go to |AdminBtn| and in the |OrchBtn| sub-menu. Create an orchestrator named ``Yorc`` with the plugin
``Yorc Orchestrator Factory : 1.0.0-SNAPSHOT``.

At this moment your orchestrator is created but not enabled. Click on your orchestrator to see the information page, and then
click on the configuration menu icon |OrchConfigBtn|.

In the Driver configuration part, add the URL of your Yorc server (should respect the format: ``http://yorc-ip:8800``) and return to the previous page to enable your orchestrator.

If Yorc is secured (ssl enabled):
  * the yorc URL should use the ``https`` protocol
  * the CA authority used to sign the Yorc certificates should be imported in the Java truststore ; otherwise, check ``insecureTL``


Configure an OpenStack Location
-------------------------------

Once your orchestrator is created and enabled, go to the locations page by clicking on |OrchLocBtn|. Create a location named ``OpenStack``
and select ``openstack`` on the infrastructure type drop-down. The details page of your location should appears. Go to |OrchLocODRBtn| and
add the following resources:

  * yorc.nodes.openstack.PublicNetwork
  * yorc.nodes.openstack.Compute

Click on the network and set ``floating_network_name`` to the name of your OpenStack public network for the tenant where the Yorc instance
is deployed.

.. image:: _static/img/orchestrator-loc-conf-net.png
   :alt: Network configuration
   :align: center


Click on the compute and set the ``image`` to the id of your image in OpenStack (in order to use our samples in next sections, please use
an Ubuntu 14.04+ or Centos 7.2+ image), the ``flavor`` to ``3`` (medium for a default OpenStack config). Set ``key_pair`` to the OpenStack
keypair that correspond to the private key that you stored under ``~/.ssh/yorc.pem`` during your Yorc server setup. Finally, in the ``endpoint``
capability of the Compute, open the ``credentials`` complex type and set the ``user`` to a user available in your image (generally ``ubuntu``
for Ubuntu cloud images)

.. image:: _static/img/orchestrator-loc-conf-compute.png
   :alt: Compute configuration
   :align: center


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
