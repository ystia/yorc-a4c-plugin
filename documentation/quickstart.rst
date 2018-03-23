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

Quick Start: Deploy your first application
==========================================

Import Samples
--------------

Click on the |ComponentsBtn| button in the navigation bar.

Locate the ``welcome-<version>-csar.zip`` and ``welcome-basic-<version>-topo.zip`` in the yorc distribution and drop them
**in this order** into the catalog's drop area.

Create a Welcome application
----------------------------

Now we have the Welcome template ready to use, we can create an application based on it. To do this, go to the |ApplicationsBtn| section.
Click on the |NewAppBtn| button and select the welcome-basic in the table at the bottom of the popup.

.. image:: _static/img/new-welcome-app.png
   :alt: Create Application
   :align: center

The application creation should redirect you on the application information page. To see your application topology,
go to |AppTopoBtn| page, you will see the following screen.

.. image:: _static/img/welcome-app-topo.png
   :alt: Welcome Application topology
   :align: center

Setup and deploy your application
---------------------------------

To deploy this new application, go on the |AppDepsBtn| sub-menu and :

  * Select your location
  * Go to the Deploy tab
  * And click on |AppDeployBtn|


.. image:: _static/img/app-location.png
   :alt: Select a location
   :align: center


.. note:: To understand all configuration available for the deployment page, please refer to the
          `Alien4Cloud application management section <http://alien4cloud.github.io/#/documentation/2.0.0/user_guide/application_management.html>`_ .


Check that your application is up and running
---------------------------------------------

On the runtime view, you can have the detailed deployment progress. Click on the side bar sub-menu |AppRuntimeBtn|


.. image:: _static/img/app-runtime-dep.png
   :alt: Application runtime view
   :align: center


When all nodes are deployed, go back in the |AppInfoBtn| sub-menu to get the Welcome application url and test it !

.. image:: _static/img/app-info-outprop.png
   :alt: Application information view
   :align: center


Next Steps: Define your own components
--------------------------------------

Please refer to `the Alien4Cloud dev guide <http://alien4cloud.github.io/community/index.html#/documentation/2.0.0/devops_guide/dev_ops_guide.html>`_
to write your own components.

.. |ComponentsBtn| image:: _static/img/components-btn.png
                   :alt: components

.. |ApplicationsBtn| image:: _static/img/application-btn.png
                     :alt: applications

.. |NewAppBtn| image:: _static/img/new-application-btn.png
               :alt: new application

.. |AppTopoBtn| image:: _static/img/app-topo-btn.png
                :alt: application topology

.. |AppDepsBtn| image:: _static/img/application-deployment-btn.png
                :alt: application deployment

.. |AppDeployBtn| image:: _static/img/app-deploy-btn.png
                  :alt: deploy button

.. |AppRuntimeBtn| image:: _static/img/app-runtime-btn.png
                   :alt: runtime button

.. |AppInfoBtn| image:: _static/img/app-info-btn.png
                :alt: information
