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

import argparse
import glob
import json
import logging

import requests
import sys

__author__ = 'Loic Albertin'

logging.basicConfig(stream=sys.stdout, level=logging.INFO)
logger = logging.getLogger(__name__)


class AlienClient(object):
    def __init__(self, alien_url):
        """
        Creates an AlienClient for a given URL
        :param alien_url: URL to Alien
        :type alien_url: str
        """
        self.alien_url = alien_url
        self.user = None
        self.password = None
        self.session = None
        self.login_status = None

    def connect(self, user, password):
        """
        Connects to Alien using a given user/password
        :param user: The user name
        :type user: str
        :param password: The password
        :type password: str
        :return: A boolean indicating the success
        :type: bool
        """
        self.user = user
        self.password = password
        payload = {'username': user, 'password': password, 'submit': 'login'}
        # post login/password
        r = requests.post("{0}/Login".format(self.alien_url), data=payload,
                          allow_redirects=False)

        self.session = requests.session()
        self.session.cookies.update(r.cookies)

        self.session.headers = {'accept': 'application/json'}

        # return authentication json object
        r = self.session.get("{0}/rest/auth/status".format(self.alien_url))
        login_status = r.json()

        if login_status['error'] is None:
            self.login_status = login_status['data']
            return True
        else:
            self.login_status = login_status['error']
            return False

    def create_orchestrator(self, name, plugin_id, plugin_bean):
        """
        Create an orchestrator
        :param name: orchestrator name
        :type name: str
        :param plugin_id:
        :type plugin_id: str
        :param plugin_bean:
        :type plugin_bean: str
        :return: The newly created orchestrator id
        :type: str
        """
        logger.info("Creating Orchestrator %s", name)
        payload = {'name': name, 'pluginId': plugin_id, 'pluginBean': plugin_bean}
        response = self.session.post("{0}/rest/orchestrators".format(self.alien_url), data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create an Orchestrator {0}: {1}".format(name, response["error"]))
        return response["data"]

    def configure_yorc_orchestrator(self, orchestrator_id, yorc_url):
        """
        Configure a Yorc orchestrator
        :param orchestrator_id: The orchestrator id
        :type orchestrator_id: str
        :param yorc_url: The Yorc manager REST API URL (format https?://ip_or_dns_name:port)
        :type yorc_url: str
        """
        logger.info("Configuring the Orchestrator to use Yorc Manager at %s", yorc_url)
        payload = {
            'urlYorc': yorc_url
        }
        logger.info(payload)
        response = self.session.put("{0}/rest/orchestrators/{1}/configuration".format(self.alien_url, orchestrator_id),
                                    data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to configure Orchestrator {0}: {1}".format(orchestrator_id, response["error"]))

    def enable_orchestrator(self, orchestrator_id):
        """
        Enable an orchestrator
        :param orchestrator_id: The orchestrator id
        :type orchestrator_id: str
        """
        logger.info("Enabling Orchestrator")
        payload = {}
        response = self.session.post("{0}/rest/orchestrators/{1}/instance".format(self.alien_url, orchestrator_id),
                                     data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to enable Orchestrator {0}: {1}".format(orchestrator_id, response["error"]))

    def create_location(self, orchestrator_id, loc_name, infra):
        logger.info("Creating Location %s", loc_name)
        payload = {'infrastructureType': infra, 'name': loc_name}
        response = self.session.post("{0}/rest/orchestrators/{1}/locations".format(self.alien_url, orchestrator_id),
                                     data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create an location {0}: {1}".format(loc_name, response["error"]))
        return response["data"]

    def create_resources_for_location(self, orchestrator_id, location_id, centos_image_id, public_net_name):
        logger.info("Creating Resources for the orchestrator")
        self.create_resource_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.Image", "Centos", centos_image_id)
        self.create_resource_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.Flavor", "Small", "2")
        self.create_resource_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.Flavor", "Medium", "3")
        self.create_resource_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.Flavor", "Large", "4")

        logger.info("auto-configure resources")
        response = self.session.get(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/auto-configure".format(self.alien_url, orchestrator_id, location_id)).json()
        if response["error"]:
            raise RuntimeError("Failed to auto-configure resources: {0}".format(response["error"]))
        for data in response["data"]:
            if data["template"]["type"] == "yorc.nodes.openstack.Compute":
                resource_id = data["id"]
                payload = {'propertyName': 'credentials', 'propertyValue': {'user': 'centos'}}
                response = self.session.post(
                    "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/capabilities/endpoint/properties".format(self.alien_url, orchestrator_id,
                                                                                                        location_id, resource_id),
                    data=json.dumps(payload)).json()
                if response["error"]:
                    raise RuntimeError("Failed to update user for resource {0}: {1}".format(data["name"], response["error"]))
                payload = {'propertyName': 'key_pair', 'propertyValue': 'yorc'}
                response = self.session.post(
                    "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                        location_id, resource_id),
                    data=json.dumps(payload)).json()
                if response["error"]:
                    raise RuntimeError("Failed to update key_pair for resource {0}: {1}".format(data["name"], response["error"]))
        self.create_volume_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.BlockStorage", "Small_Volume", "10 GIB")
        self.create_volume_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.BlockStorage", "Medium_Volume", "30 GIB")
        self.create_volume_for_location(orchestrator_id, location_id, "yorc.nodes.openstack.BlockStorage", "Large_Volume", "50 GIB")

        self.create_public_network_for_location(orchestrator_id, location_id, public_net_name)

    def create_resource_for_location(self, orchestrator_id, location_id, resource_type, resource_name, resource_id):
        logger.info("Creating Resource %s of type %s", resource_name, resource_type)
        payload = {'resourceType': resource_type, 'resourceName': resource_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources".format(self.alien_url, orchestrator_id, location_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))
        res_id = response["data"]["resourceTemplate"]["id"]
        logger.info("Configuring Id {0}".format(res_id))
        payload = {'propertyName': 'id', 'propertyValue': resource_id}
        response = self.session.post(
            "{0}/rest/latest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                       location_id, res_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))

    def create_volume_for_location(self, orchestrator_id, location_id, resource_type, resource_name, size):
        logger.info("Creating Volume %s of type %s", resource_name, resource_type)
        payload = {'resourceType': resource_type, 'resourceName': resource_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources".format(self.alien_url, orchestrator_id, location_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))
        res_id = response["data"]["resourceTemplate"]["id"]
        payload = {'propertyName': 'size', 'propertyValue': size}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                location_id, res_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))

    def create_public_network_for_location(self, orchestrator_id, location_id, network_name):
        logger.info("Creating Public Network %s", network_name)
        payload = {'resourceType': 'yorc.nodes.openstack.PublicNetwork', 'resourceName': network_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources".format(self.alien_url, orchestrator_id, location_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create network resource {0}: {1}".format(network_name, response["error"]))
        res_id = response["data"]["resourceTemplate"]["id"]
        payload = {'propertyName': 'floating_network_name', 'propertyValue': network_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                location_id, res_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create network resource {0}: {1}".format(network_name, response["error"]))

    def upload_yorc_plugin(self):
        logger.info("Uploading the yorc plugin")
        artifact = glob.glob('alien4cloud-yorc-plugin-*.zip')[0]
        self.session.headers = {'accept': 'application/json'}
        payload = {'file': open(artifact, 'rb')}
        response = self.session.post("%s/rest/plugins" % self.alien_url, files=payload).json()
        if response["error"]:
            raise RuntimeError("Failed to upload yorc plugin: {0}".format(response["error"]))

    # upload csar into a4c
    def upload_csar(self, name):
        artifacts = glob.glob('%s-*-csar.zip' % name)
        for artifact in artifacts:
            logger.info("Uploading %s" % artifact)
            self.session.headers = {'accept': 'application/json'}
            payload = {'file': open(artifact, 'rb')}
            response = self.session.post("%s/rest/csars" % self.alien_url, files=payload).json()
            if response["error"]:
                logger.warn(str(response))
                raise RuntimeError("Failed to upload csars: {0}".format(response["error"]))


def main():
    parser = argparse.ArgumentParser(description='Configure Alien4Cloud')
    parser.add_argument('--alien-ip', dest='alien_ip', action='store',
                        type=str, default="localhost",
                        help='The Alien IP')
    parser.add_argument('--manager-url', dest='manager_url', action='store',
                        type=str, nargs=1, required=True,
                        help='The Yorc manager REST API URL (format https?://ip_or_dns_name:port)')
    parser.add_argument('--agent-image-id', dest='centos_image_id', action='store',
                        type=str, nargs=1, required=True,
                        help='The Image ID for Centos OS')
    parser.add_argument('--public-network-name', dest='public_net_name', action='store',
                        type=str, default="public-starlings",
                        help='The Name of the OpenStack Public Network')
    args = parser.parse_args()
    alien = AlienClient("http://{0}:8088/".format(args.alien_ip))
    alien.connect("admin", "admin")

    # Upload the alien4cloud-yorc-plugin
    alien.upload_yorc_plugin()

    # Upload all csar from the bdcf catalog
    # Order is important : there are dependencies
    alien.upload_csar('java')
    alien.upload_csar('consul')
    alien.upload_csar('haproxy')
    alien.upload_csar('mysql')
    alien.upload_csar('rstudio')
    alien.upload_csar('mongodb')
    alien.upload_csar('elasticsearch')
    alien.upload_csar('kafka')
    alien.upload_csar('kibana')
    alien.upload_csar('logstash')
    alien.upload_csar('mapr')
    alien.upload_csar('beats')
    alien.upload_csar('nutch')

    # Create the Yorc orchestrator
    alien.session.headers = {'accept': 'application/json', 'content-type': 'application/json'}
    orchestrator_id = alien.create_orchestrator("Yorc", "alien4cloud-yorc-plugin", "yorc-orchestrator-factory")
    print orchestrator_id
    alien.configure_yorc_orchestrator(orchestrator_id, args.manager_url[0])
    alien.enable_orchestrator(orchestrator_id)
    location_id = alien.create_location(orchestrator_id, "openstack", "OpenStack")
    alien.create_resources_for_location(orchestrator_id, location_id, args.centos_image_id[0], args.public_net_name)


if __name__ == "__main__":
    main()
