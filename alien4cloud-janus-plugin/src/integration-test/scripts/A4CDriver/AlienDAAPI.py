#XBD team

import json
import urllib
import time
from Util import Util

class AlienDAAPI():


    # lazy creation self.s => session for requests

############ Proxies #######################
    def __init__(self, ut):

        self.util = ut

        self.urlAuthent = "http://" + self.util.A4CHostsPort+ "/login?username=admin&password=admin&submit=Login"
        self.urlPlugins= "http://" + self.util.A4CHostsPort+ "/rest/plugins/"
        self.urlOrchs= "http://" + self.util.A4CHostsPort+ "/rest/orchestrators/"
        self.urlTemplateUpload = "http://" + self.util.A4CHostsPort+ "/rest/csars"
        self.urlTemplateSearch = "http://" + self.util.A4CHostsPort+ "/rest/templates/topology"
        self.urlTemplate = "http://" + self.util.A4CHostsPort+ "/rest/templates"
        self.urlApplication = "http://" + self.util.A4CHostsPort+ "/rest/applications" #/search


        if (self.util.vpnOn == "true") :
            import requesocks as requests
        else :
            import requests

        self.s=requests.Session()

        if (self.util.vpnOn == "true") :
            proxies = {
            'http': 'socks5://127.0.0.1:20000',
            'https': 'socks5://127.0.0.1:20000',
            }
            self.s.proxies=proxies



############ Login #######################
    def login(self):
        r = self.s.post(self.urlAuthent)
        printR(r,"Authentification ")



############ Orchestrators #######################
    def retrieveOrchId(self,partName):
        response = self.s.get(self.urlOrchs)
        respObj = json.loads(response.content)
        #print json.dumps(respObj, indent = 4)

        for orch in respObj["data"]["data"]:
                if partName  in orch["pluginId"]:
                    printR(response,"retrieveOrchId")
                    return orch["id"]

    def removeOrch(self,id):
        response = self.s.delete(self.urlOrchs+"/"+str(id))
        printR(response,"removeOrch")
        return response

    def addOrch(self,name,pid,bean):
        timestr = time.strftime("%Y%m%d-%H%M%S")
        n =  name+"_"+timestr

        data = {'name':   n,
        'pluginId': pid,
        'pluginBean':  bean}

        response = self.util.postJsonObject(self.s, self.urlOrchs, data)
        printR(response,"addOrch")
        return response

    def enableOrch(self,id):
        url = self.urlOrchs+str(id) + "/instance"
        response = self.s.post(url)
        printR(response,"enableOrch")

    def disableOrch(self,id):
        url = self.urlOrchs+str(id) + "/instance"
        response = self.s.delete(url)
        printR(response,"disableOrch")

############ Plugins #######################


    def retrievePlugin(self,partName):
        r = self.s.get(self.urlPlugins)
        response = json.loads(r.content)
        #print json.dumps(response, indent = 4)

        for plug in response["data"]["data"]:
            if partName  in plug["id"]:
                #return  (urllib.quote(plug["id"]),str(urllib.quote(plug["descriptor"]["componentDescriptors"][0]["beanName"])))
                printR(r,"retrievePlugin")
                return  (str(plug["id"]),str(plug["descriptor"]["componentDescriptors"][0]["beanName"]))

    def removePlugin(self,id):
        response = self.s.delete(self.urlPlugins+"/"+ urllib.quote(id))
        printR(response,"removePlugin")
        return response

    def addPlugin(self,fileName):
        files = {'file': open(fileName, 'rb')}
        response = self.s.post(self.urlPlugins,files=files)
        printRR(response, "addPlugin ",str(fileName))
        return response

############ Location #######################
    def addLoc(self,id,name,infra):
        timestr = time.strftime("%Y%m%d-%H%M%S")
        n =  name+"_"+timestr

        data = {"name":n,"infrastructureType":infra}

        url = self.urlOrchs+ "/"+str(id) +"/locations"

        response = self.util.postJsonObject(self.s, url, data)
        printR(response,"addLoc")

        return response

    def getFirstloc(self,id):

        url = self.urlOrchs+ "/"+str(id) +"/locations"
        r = self.s.get(url)
        printR(r,"getFirstloc")
        response = json.loads(r.content)
        if len(response["data"]) > 0 :
            return response["data"][0]["location"]["id"]
        return None

############ Ressource #######################

    def addRes(self,oid,lid,name, resType):
        timestr = time.strftime("%Y%m%d-%H%M%S")
        n =  name+"_"+timestr

        data = {"resourceType": resType,"resourceName":n}

        url = self.urlOrchs+oid +"/locations/"+lid+"/resources"
        response = self.util.postJsonObject(self.s, url, data)
        printR(response,"addRes")
        return response


    def getFirstRes(self,oid,lid):
        url = self.urlOrchs+ "/"+oid+"/locations/"+lid
        r = self.s.get(url)
        printR(r,"getFirstRes")
        response = json.loads(r.content)
        return response["data"]["resources"]["nodeTemplates"][0]["id"]

    def setPropRes(self,oid,lid,rid, properties):
        try:
            url = self.urlOrchs+oid +"/locations/"+lid+"/resources/"+rid+"/template/properties"
            for prop in properties:
                response = self.util.postJsonObject(self.s, url, prop)
                printR(response,"setProperty")
        except:
            print "Unexpected error but work..."

        return response

############ Template #######################
    def addTemplate(self,fileName):
        files = {'file': open(fileName, 'rb')}
        response = self.s.post(self.urlTemplateUpload,files=files)
        printRR(response, "addTemplate ",str(fileName))
        return response

    def getFirstTemplate(self):
        url = self.urlTemplateSearch+"/search"
        data = {"from":"0","size":"1"}
        r = self.util.postJsonObject(self.s, url, data)
        printR(r,"getFirstTemplate")
        response = json.loads(r.content)
        if len(response["data"]["data"]) > 0 :
            return response["data"]["data"][0]["id"]
        return None


    def removeTemplate(self,tid):
        url = self.urlTemplateSearch+"/"+tid
        response = self.s.delete(url)
        printR(response,"removeTemplate")
        return response



############ Applicaiton #######################
    def getFirstAppli(self):
        url = self.urlApplication + "/search"
        data = {"from":"0","size":"1"}
        r = self.util.postJsonObject(self.s, url, data)
        printR(r,"getFirstAppli")
        response = json.loads(r.content)
        if len(response["data"]["data"]) > 0 :
            return response["data"]["data"][0]["id"]
        return None

    def removeAppli(self,aid):
        url = self.urlApplication+"/"+aid
        response = self.s.delete(url)
        printR(response,"removeAppli")
        return response

#{"name":"mynewapp","description":"mynewapp","topologyTemplateVersionId":"530450c6-d6ae-4511-be5c-e3289a903503"}
#POST http://172.16.118.132:8088/rest/latest/applications

    def addAppli(self,name,des,tvid):
        timestr = time.strftime("%Y%m%d-%H%M%S")
        n =  name+"_"+timestr

        data = {'name':   n,
        'description': des,
        'topologyTemplateVersionId':  tvid}

        response = self.util.postJsonObject(self.s, self.urlApplication, data)
        printR(response,"addAppli")
        return response

############ DEPLOYMENT #######################

    def getFirstEnv(self, applicationId):
        data = {'from':   '0', 'size': '1'}
        response = self.util.postJsonObject(self.s, self.urlApplication + "/" + applicationId + "/environments/search", data)
        printR(response,"getFirstEnv")
        response = json.loads(response.content)
        if len(response["data"]["data"]) > 0 :
            return response["data"]["data"][0]["id"]
        return None

    def setDeployPolicy(self, applicationId, applicationEnvironmentId, pluginId):
        #orchId = "c58fa029-14fa-4531-98fc-d1d059ee686f"
        orchId = self.retrieveOrchId(pluginId);
        locId = self.getFirstloc(orchId);
        data = {'orchestratorId':   orchId, "groupsToLocations": {"_A4C_ALL" : locId}}

        response = self.util.postJsonObject(self.s, self.urlApplication + "/" + applicationId + "/environments/" + applicationEnvironmentId + "/deployment-topology/location-policies", data)
        printR(response,"setDeployPolicy")
        return response

    def deployAppli(self, pluginId):
        applicationId = self.getFirstAppli();
        applicationEnvironmentId = self.getFirstEnv(applicationId)
        data = {'applicationId':   applicationId,
        'applicationEnvironmentId': applicationEnvironmentId}

        self.setDeployPolicy(applicationId, applicationEnvironmentId, pluginId)
        response = self.util.postJsonObject(self.s, self.urlApplication + "/deployment", data)
        printR(response,"deployAppli")
        return response

    def unDeployAppli(self):
        applicationId = self.getFirstAppli();
        applicationEnvironmentId = self.getFirstEnv(applicationId)
        data = {'applicationId':   applicationId,
        'applicationEnvironmentId': applicationEnvironmentId}

        response = self.util.deleteJsonObject(self.s, self.urlApplication + "/" + applicationId + "/environments/" + applicationEnvironmentId + "/deployment", data)
        printR(response,"unDeployAppli")
        return response

#http://172.16.118.132:8088/rest/latest/templates/ca01847d-3614-49e3-b7ff-36b55944b276/versions/search
    def getFirstVersionTemplate(self,tid):
        url = self.urlTemplate + "/" + tid+ "/versions/search"
        data = {"from":"0","size":"1"}
        r = self.util.postJsonObject(self.s, url, data)
        printR(r,"getFirstVersionTemplate")
        response = json.loads(r.content)
        if len(response["data"]["data"]) > 0 :
            return response["data"]["data"][0]["id"]
        return None

def printR(response, name):
    print name    + " \t\t " + str(response)
    if "200" not in str(response) and "201" not in str(response) :
        print response.content


def printRR(response, name, info):
    printR(response, name)
    print "\t\t" + info
    print " "
