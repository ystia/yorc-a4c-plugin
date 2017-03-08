import argparse
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
        headers = {'accept': 'application/json'}
        self.session.headers = headers

        # return authentication json object
        r = self.session.get("{0}/rest/auth/status".format(self.alien_url))
        login_status = r.json()

        headers = {'accept': 'application/json', 'content-type': 'application/json'}
        self.session.headers = headers

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

    def configure_janus_orchestrator(self, orchestrator_id, janus_ip):
        """
        Configure a Janus orchestrator
        :param orchestrator_id: The orchestrator id
        :type orchestrator_id: str
        :param janus_ip: The Janus manager ip
        :type janus_ip: str
        """
        logger.info("Configuring the Orchestrator to use Janus Manager at %s", janus_ip)
        payload = {
            'urlJanus': 'http://{0}'.format(janus_ip)
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
        self.create_resource_for_location(orchestrator_id, location_id, "janus.nodes.openstack.Image", "Centos", centos_image_id)
        self.create_resource_for_location(orchestrator_id, location_id, "janus.nodes.openstack.Flavor", "Small", "2")
        self.create_resource_for_location(orchestrator_id, location_id, "janus.nodes.openstack.Flavor", "Medium", "3")
        self.create_resource_for_location(orchestrator_id, location_id, "janus.nodes.openstack.Flavor", "Large", "4")

        logger.info("auto-configure resources")
        response = self.session.get(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/auto-configure".format(self.alien_url, orchestrator_id, location_id)).json()
        if response["error"]:
            raise RuntimeError("Failed to auto-configure resources: {0}".format(response["error"]))
        for data in response["data"]:
            if data["template"]["type"] == "janus.nodes.openstack.Compute":
                resource_id = data["id"]
                payload = {'propertyName': 'user', 'propertyValue': 'cloud-user'}
                response = self.session.post(
                    "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                        location_id, resource_id),
                    data=json.dumps(payload)).json()
                if response["error"]:
                    raise RuntimeError("Failed to update user for resource {0}: {1}".format(data["name"], response["error"]))
                payload = {'propertyName': 'key_pair', 'propertyValue': 'janus'}
                response = self.session.post(
                    "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                        location_id, resource_id),
                    data=json.dumps(payload)).json()
                if response["error"]:
                    raise RuntimeError("Failed to update key_pair for resource {0}: {1}".format(data["name"], response["error"]))
        self.create_volume_for_location(orchestrator_id, location_id, "janus.nodes.openstack.BlockStorage", "Small_Volume", "10 GIB")
        self.create_volume_for_location(orchestrator_id, location_id, "janus.nodes.openstack.BlockStorage", "Medium_Volume", "30 GIB")
        self.create_volume_for_location(orchestrator_id, location_id, "janus.nodes.openstack.BlockStorage", "Large_Volume", "50 GIB")

        self.create_public_network_for_location(orchestrator_id, location_id, public_net_name)

    def create_resource_for_location(self, orchestrator_id, location_id, resource_type, resource_name, resource_id):
        logger.info("Creating Resource %s of type %s", resource_name, resource_type)
        payload = {'resourceType': resource_type, 'resourceName': resource_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources".format(self.alien_url, orchestrator_id, location_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))
        res_id = response["data"]["id"]
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
        res_id = response["data"]["id"]
        payload = {'propertyName': 'size', 'propertyValue': size}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                location_id, res_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create resource {1}:{0}: {2}".format(resource_type, resource_name, response["error"]))

    def create_public_network_for_location(self, orchestrator_id, location_id, network_name):
        logger.info("Creating Public Network %s", network_name)
        payload = {'resourceType': 'janus.nodes.openstack.PublicNetwork', 'resourceName': network_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources".format(self.alien_url, orchestrator_id, location_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create network resource {0}: {1}".format(network_name, response["error"]))
        res_id = response["data"]["id"]
        payload = {'propertyName': 'floating_network_name', 'propertyValue': network_name}
        response = self.session.post(
            "{0}/rest/orchestrators/{1}/locations/{2}/resources/{3}/template/properties".format(self.alien_url, orchestrator_id,
                                                                                                location_id, res_id),
            data=json.dumps(payload)).json()
        if response["error"]:
            raise RuntimeError("Failed to create network resource {0}: {1}".format(network_name, response["error"]))

def main():
    parser = argparse.ArgumentParser(description='Configure Alien4Cloud')
    parser.add_argument('--alien-ip', dest='alien_ip', action='store',
                        type=str, default="localhost",
                        help='The Alien IP')
    parser.add_argument('--manager-ip', dest='manager_ip', action='store',
                        type=str, nargs=1, required=True,
                        help='The Janus IP')
    parser.add_argument('--agent-image-id', dest='centos_image_id', action='store',
                        type=str, nargs=1, required=True,
                        help='The Image ID for Centos OS')
    parser.add_argument('--public-network-name', dest='public_net_name', action='store',
                        type=str, default="public_starlings",
                        help='The Name of the OpenStack Public Network')
    args = parser.parse_args()
    alien = AlienClient("http://{0}:8088/".format(args.alien_ip))
    alien.connect("admin", "admin")

    orchestrator_id = alien.create_orchestrator("Janus", "alien4cloud-Janus-plugin", "Janus-orchestrator-factory")
    print orchestrator_id
    alien.configure_janus_orchestrator(orchestrator_id, args.manager_ip[0])
    alien.enable_orchestrator(orchestrator_id)
    location_id = alien.create_location(orchestrator_id, "openstack", "OpenStack")
    alien.create_resources_for_location(orchestrator_id, location_id, args.centos_image_id[0], args.public_net_name)

if __name__ == "__main__":
    main()

