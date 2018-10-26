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


Upgrades
========

Upgrading Yorc engine is described in `a separated document <https://yorc.readthedocs.io/en/stable/upgrade.html>`_.

.. note:: A rolling upgrade without interruption feature is planned for future versions.

Alien4Cloud Yorc Plugin upgrade
-------------------------------

To upgrade yorc-a4c-plugin you just have to:

# move to the Alien4Cloud installation directory
# move in `init/plugins` directory
# remove any existing yorc-a4c-plugin
# download and install the new version of the yorc-a4c-plugin in this directory
# restart Alien4Cloud

Alien4Cloud upgrade
-------------------

Upgrading Alien4Cloud without loosing deployed applications requires an premium version
of Alien4Cloud. As part of the startup process Alien4Cloud will update it's internal
database automatically.
