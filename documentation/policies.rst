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

Applying policies
=================

Applying a Server Group Anti-affinity placement policy on OpenStack
----------------------------------------

Let's imagine the use case of a resilient application on OpenStack:

This application is scalable but to enhance HA, you want each app instance located in a different
physical machine. So, if a breakdown occurs on one specific host, it doesn't touch all app instances but only one.

This can be done by applying the ``yorc.openstack.policies.ServerGroupPolicy`` on OpenStack with an anti-affinity policy.

Let's do it !

After configuring your OpenStack location as described :ref:`here <location_config_openstack_section>`, click on the |OrchLocPolicies| button, select ``catalog`` and use the search to find the ServerGroupPolicy as below.

.. image:: _static/img/search-servergroup-policy.png
   :alt: Search serverGroup policy
   :align: center

Next, drag-and-drop the policy in the new policies resources list of your OpenStack location.
Rename the resource by example ``AntiAffinityPolicy``. Select ``anti-affinity`` in the policy property.
You must finally have this configuration:

.. image:: _static/img/servergroup-policy-resource.png
   :alt: Configure your serverGroup policy
   :align: center

Now, your OpenStack location is configured with a Server Group anti-affinity placement policy.
You can apply it on your topology application by using abstract and so non infrastructure-dependent nodes and policies that let you deploy your application as well on OpenStack or on GCP if another specific placement policy is implemented for GCP too.

Select your application and go to the ``Topology Editor``. Click on the |TopologyEditorPolicies| button on the vertical blue bar on the left.
Click on the ``+ Add policies`` button, search the abstract policy node ``Placement`` (tosca.policies.Placement) and drag-and-drop it on the policies list of your topology.

Then you can select the ``Targets`` of the placement policy, i.e in this case, the node name of the compute instances you want not to be located on the same host.

Valid targets for applying ``ServerGroupPolicy`` are:

  * One scalable compute node template with at least two as max instances number.
  * At least two different compute node templates, not necessarily scalable.

.. image:: _static/img/placement-topology-editor.png
   :alt: Add placement policy to your topology application
   :align: center


That's it ! You just have to check the correct policy matching after choosing the Openstack location as the ``ServerGroupPolicy`` derives from ``tosca.policies.Placement``

Deploy the application and enjoy !

.. image:: _static/img/policy-matching.png
   :alt: Policy matching before deploying application
   :align: center


.. |OrchLocPolicies| image:: _static/img/policies-button.png
                   :alt: policies button

.. |TopologyEditorPolicies| image:: _static/img/topology-policies-button.png
                  :alt: policies button
