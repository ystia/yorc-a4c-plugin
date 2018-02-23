#
# Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys

sys.path.insert(0, 'src/integration-test/a4c-api-wrapper')
import Main

t = "main"
Main.init("./src/integration-test/scripts/A4CDriverConf.json")
Main.chooseLocation("OpenStack")


Main.removeAllAppliByName(Main.run("", t), "OpenStack")
Main.removeAllOrchByName(Main.run("", t), "OpenStack")
Main.removeAllAppliByName(Main.run("", t), "Slurm")
Main.removeAllOrchByName(Main.run("", t), "Slurm")
Main.clean(Main.run("", t))
Main.adp(Main.run("", t))

Main.ado(Main.run("", t))

Main.eno(Main.run("", t))

#If a component must be updated
#Main.rmc(Main.run("", t))
#Main.adc(Main.run("", t))


Main.adl(Main.run("", t))
Main.adr(Main.run("", t))
Main.adt(Main.run("", t))
Main.ada(Main.run("", t))
Main.deploy(Main.run("", t))
Main.undeploy(Main.run("", t))
