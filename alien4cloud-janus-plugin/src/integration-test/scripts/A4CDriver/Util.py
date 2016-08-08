#XBD team
import json
import urllib
import time


class Util():

    #JSON
    PLUGIN_FILE_PATH = "PLUGIN_FILE_PATH"
    PLUGIN_ID = "PLUGIN_ID"
    VPN_ON = "VPN_ON"
    PROXY_USE = "PROXY_USE"
    A4C_HOSTS_PORT = "A4C_HOSTS_PORT"
    TEMPLATE_FILE_PATH = "TEMPLATE_FILE_PATH"

    confFileNameTemplate = "A4CDriverConf_Template"

    LOCATION = {
        "Slurm": {
            "infra": "Slurm",
            "resType": "janus.nodes.slurm.Compute",
            "properties": [
                {
                    "propertyName":"imageId",
                    "propertyValue":"2"
                },
                {
                    "propertyName":"gpuType",
                    "propertyValue":"3"
                },
                {
                    "propertyName":"flavorId",
                    "propertyValue":"2"
                }
            ]
        },
        "OpenStack": {
            "infra": "OpenStack",
            "resType": "janus.nodes.openstack.Compute",
            "properties": [
                {
                    "propertyName":"imageId",
                    "propertyValue":"1"
                },
                {
                    "propertyName":"flavorId",
                    "propertyValue":"2"
                }
            ]
        }
    }


    #conf facilities => loadConfs()
    # conf=  {}
    # A4CHostsPort = ""
    # proxyUse= ""
    # vpnOn = ""
    # pluginFilePath = ""
    # templateFilePath = ""

    def postJsonObject(self,session,url,jsonString):
        headers = {'content-type': 'application/json'}
        response = session.post(url,data=json.dumps(jsonString),headers=headers)
        return response

    def deleteJsonObject(self,session,url,jsonString):
        headers = {'content-type': 'application/json'}
        response = session.delete(url,data=json.dumps(jsonString),headers=headers)
        return response

    def loadConfs(self,file):
        with open(file, 'r') as f:
            dict=json.load(f)


        self.conf =  dict
        self.A4CHostsPort = self.conf[self.A4C_HOSTS_PORT]
        self.vpnOn = self.conf[Util.VPN_ON]
        self.pluginFilePath = self.conf[self.PLUGIN_FILE_PATH]
        self.pluginId = self.conf[self.PLUGIN_ID]

        self.proxyUse = self.conf[self.PROXY_USE]
        self.templateFilePath = self.conf[self.TEMPLATE_FILE_PATH]

        print "A4CHostsPort: "+self.A4CHostsPort
        print "pluginFilePath: "+self.pluginFilePath
        print "pluginId: "+ self.pluginId
        print "vpnOn: " +self.vpnOn
        print "proxyUse: "+ self.proxyUse


    def getConf(self,prop):
        return Util.conf[prop]


    def writeConfTemplate(self):
        data = {
            Util.PLUGIN_FILE_PATH:   'path/to/the/plugin.archive',
            Util.TEMPLATE_FILE_PATH: 'path/to/the/template.archive',
            Util.VPN_ON: "false",
            Util.PROXY_USE:  'requests will be sent through localhost 20000 socks 5',
            Util.A4C_HOSTS_PORT: 'XX.XX.XX.XX:8088'
            }

        with open(Util.confFileNameTemplate, 'w') as f:
            json.dump(data, f)
        print "setConfTemplate done"
