Install Alien4Cloud Janus Plugin and requirements
=================================================

Host requirements
-----------------

  * A machine with Linux or MacOS Operating System: Alien4Cloud can run on windows OS too but we recommend Unix based OS. Moreover the default package only includes sh script.
  * JAVA: Ensure that you have at least JAVA version 8 or higher installed on your working station. If not, just install java following instructions `here <https://www.java.com/fr/download/manual.jsp>`_ .
  * A supported web browser (`check versions here <http://alien4cloud.github.io/#/documentation/1.4.0/admin_guide/supported_platforms.html>`_ ).

Execution environment requirements
----------------------------------

An HTTP(S) access to a running and properly configured (for OpenStack deployements) instance of Janus is required. Please refer to the Janus engine documentation for more details on how to set it up.

Alien4Cloud Setup
-----------------

Please refer to the `online documentation of Alien4Cloud (section "Install Alien4Cloud") <http://alien4cloud.github.io/#/documentation/1.4.0/getting_started/getting_started.html>`_ in order to install Alien4Cloud properly.

Please be sure to use `Alien4Cloud 1.4.1 <http://fastconnect.org/maven/service/local/artifact/maven/redirect?r=opensource&g=alien4cloud&a=alien4cloud-dist&v=1.4.1&p=tar.gz&c=dist>`_ !

Then start Alien4Cloud as described in the `online documentation (section "Start Alien4Cloud") <http://alien4cloud.github.io/#/documentation/1.4.0/getting_started/getting_started.html>`_

Setup Alien4Cloud security
~~~~~~~~~~~~~~~~~~~~~~~~~~

Please refer to the `Security section of Alien4Cloud documentation <http://alien4cloud.github.io/#/documentation/1.4.0/admin_guide/security.html>`_ to run Alien4Cloud in secured mode.
The main steps are:

  * Generate key and PEM cerificate for the Alien4Cloud server. We advise you to use the same CA as the one used to sign the Janus PEM certificates (see "Run Janus in Secured mode" chapter in Janus documentation)
  * Create a truststore and import the CA certificate to it
  * Create a keystore. Declare the keystore in the ssl section of the configuration file (**config/alien4cloud-config.yml**)
  * Import the CA certificate to the Java truststore

Alien4Cloud Janus Plugin installation
-------------------------------------

Log into the Alien4Cloud UI as described in the previous paragraph.

Then go to |AdminBtn| and in the |PluginsBtn| sub-menu. Then drop the file named alien4cloud-janus-plugin-1.0.0-SNAPSHOT.zip in the drop area.

The next step will be to configure a new **orchestrator** and a **location** (see the next section).

.. |AdminBtn| image:: _static/img/administration-btn.png
              :alt: administration

.. |PluginsBtn| image:: _static/img/plugins-btn.png
                :alt: plugins



