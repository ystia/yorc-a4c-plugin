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

Install Alien4Cloud Yorc Plugin and requirements
================================================

Host requirements
-----------------

  * A machine with Linux or MacOS Operating System: Alien4Cloud can run on windows OS too but we recommend Unix based OS. Moreover the default package only includes sh script.
  * JAVA: Ensure that you have at least JAVA version 8 or higher installed on your working station. If not, just install java following instructions `here <https://www.java.com/fr/download/manual.jsp>`_ .
  * A supported web browser (`check versions here <http://alien4cloud.github.io/#/documentation/2.0.0/admin_guide/supported_platforms.html>`_ ).

Execution environment requirements
----------------------------------

An HTTP(S) access to a running and properly configured instance of Yorc is required. Please refer to the Yorc engine documentation for more details on how to set it up.

Alien4Cloud Setup
-----------------

Please refer to the `online documentation of Alien4Cloud (section "Install Alien4Cloud") <http://alien4cloud.github.io/#/documentation/2.0.0/getting_started/getting_started.html>`_ in order to install Alien4Cloud properly.

Please be sure to use `Alien4Cloud 2.0.0 <http://fastconnect.org/maven/service/local/artifact/maven/redirect?r=opensource&g=alien4cloud&a=alien4cloud-dist&v=2.0.0-SM5&p=tar.gz&c=dist>`_ !

Then start Alien4Cloud as described in the `online documentation (section "Start Alien4Cloud") <http://alien4cloud.github.io/#/documentation/2.0.0/getting_started/new_getting_started.html>`_

Setup Alien4Cloud security
~~~~~~~~~~~~~~~~~~~~~~~~~~

Please refer to the `Security section of Alien4Cloud documentation <http://alien4cloud.github.io/#/documentation/2.0.0/admin_guide/security.html>`_ to run Alien4Cloud in secured mode.
The main steps are:

  * Generate key and PEM cerificate for the Alien4Cloud server. We advise you to use the same CA as the one used to sign the Yorc PEM certificates (see "Run Yorc in Secured mode" chapter in Yorc documentation). This is required if client authentication is needed.
  * Create a truststore and import the CA certificate to it
  * Create a keystore. Declare the keystore in the ssl section of the configuration file (**config/alien4cloud-config.yml**)
  * Import the CA certificate to the Java truststore

Alien4Cloud Yorc Plugin installation
------------------------------------

Log into the Alien4Cloud UI as described in the previous paragraph.

Then go to |AdminBtn| and in the |PluginsBtn| sub-menu. Then drop the file named alien4cloud-yorc-plugin-|version|.zip in the drop area.

The next step will be to configure a new Yorc **orchestrator** and a **location** (see the next section).

.. |AdminBtn| image:: _static/img/administration-btn.png
              :alt: administration

.. |PluginsBtn| image:: _static/img/plugins-btn.png
                :alt: plugins



