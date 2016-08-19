#XBD team
from AlienDAAPI import AlienDAAPI
import sys
import json
from Util import Util

#Util().writeConfTemplate()
#sys.exit(0)


if len(sys.argv) < 2:
    print "Usage : python Main.py confFile"
    sys.exit(0)
else:
    confFile = sys.argv[1]

util = Util()
util.loadConfs(confFile)

alien  =  AlienDAAPI(util)

alien.login()

location = None

def printt(tab,name):
    print tab + " => "+ name

def rmp(tab):
    printt(tab,"rmp")
    pid =  alien.retrievePlugin(util.pluginId)
    if pid is None:
        print "rmp nothing to remove"
    else :
        alien.removePlugin(pid[0])

def rmo(tab):
    printt(tab,"rmo")
    oid = alien.retrieveOrchId(util.pluginId)
    if oid is None:
        print "rmo nothing to remove"
    else :
        #alien.dio(oid)
        alien.removeOrch(oid)

def adp(tab):
    printt(tab,"adp")
    alien.addPlugin(util.pluginFilePath)

def ado(tab):
    printt(tab,"ado")
    pid =  alien.retrievePlugin(util.pluginId)
    if pid is None:
        print "addo no plugin"
    else :
        alien.addOrch(location["infra"] + "_orchestrator", pid[0],pid[1])

def eno(tab):
    printt(tab,"eno")
    oid = alien.retrieveOrchId(util.pluginId)
    if oid is None:
        print "eno no orchestrator"
    else :
        alien.enableOrch(oid)

def dio(tab):
    printt(tab,"dio")
    oid = alien.retrieveOrchId(util.pluginId)
    if oid is None:
        print "dio no orchestrator"
    else :
        alien.disableOrch(oid)

def adl(tab):
    printt(tab,"adl")
    oid = alien.retrieveOrchId(util.pluginId)
    alien.addLoc(oid, location["infra"] + "_location",location["infra"])

def clean(tab):
    printt(tab,"clean")
    ti = run(tab,"clean")
    dio(ti) ;    rmo(ti) ;    rmp(ti) ; rmt(ti) ; rma(ti)


def adr(tab):
    printt(tab,"adr")
    oid = alien.retrieveOrchId(util.pluginId)
    lid=alien.getFirstloc(oid)
    alien.addRes(oid,lid,location["infra"] + "_res", location["resType"])
    setPR(run(tab,"adr"))


def setPR(tab):
    printt(tab,"setPR")
    oid = alien.retrieveOrchId(util.pluginId)
    lid = alien.getFirstloc(oid)
    rid = alien.getFirstRes(oid,lid)
    alien.setPropRes(oid,lid,rid, location["properties"])

def adt(tab):
    printt(tab,"adt")
    alien.addTemplate(util.templateFilePath)

def rmt(tab):
    printt(tab,"rmt")
    tid = alien.getFirstTemplate()
    if tid is None:
        print "rmt no template"
    else :
        alien.removeTemplate(tid)

def ada(tab):
    printt(tab,"ada")
    tid = alien.getFirstTemplate()
    if tid is None:
        print "ada no template"
        return
    tvid= alien.getFirstVersionTemplate(tid)
    if tvid is None:
        print "ada no version template"
        return
    alien.addAppli(location["infra"] + "_appli", location["infra"] + " application description", tvid)


def rma(tab):
    printt(tab,"rma")
    aid = alien.getFirstAppli()
    if aid is None:
        print "rma no appli"
        return
    alien.removeAppli(aid)

def deploy(tab):
    printt(tab,"deploy")
    alien.deployAppli(util.pluginId)

def undeploy(tab):
    printt(tab,"undeploy")
    alien.unDeployAppli();

def run(tab,new):
    if tab == "":
        print "########################"
    return tab+" => "+new

def chooseLocation(name):
    global location
    location = util.LOCATION[name];

#init dev mode
t = "main"
#chooseLocation("Slurm")
chooseLocation("OpenStack")
clean(run("",t)) ;
adp(run("",t)) ; ado(run("",t)) ; eno(run("",t)); adt(run("",t)); adl(run("",t)) ; adr(run("",t))  ; ada(run("",t))
deploy(run("",t))
undeploy(run("",t))

sys.exit()
#oid = alien.retrieveOrchId(util.pluginId)
#lid = alien.getFirstloc(oid)
#rid = alien.getFirstRes(oid,lid)
#alien.setPropRes(oid,lid,rid)

cmd = ""

while cmd != "exit":
    cmd =  raw_input('rm[p|o|t], ad[p|o|l|r|t], eno, dio, help, exit : ')
    if cmd=="clean": clean(run("",t))
    if cmd=="deploy": deploy(run("",t))
    if cmd=="undeploy": undeploy(run("",t))
    if cmd=="rmp": rmp(run("",t))
    if cmd=="rmo": rmo(run("",t))
    if cmd=="rmt": rmt(run("",t))
    if cmd=="adp": adp(run("",t))
    if cmd=="ado": ado(run("",t))
    if cmd=="eno": eno(run("",t))
    if cmd=="dio": dio(run("",t))
    if cmd=="adl": adl(run("",t))
    if cmd=="adr": adr(run("",t))
    if cmd=="adt": adt(run("",t))
    if cmd=="rma": rma(run("",t))
    if cmd=="help":
        print "clean, deploy, undeploy, help exit"
        print "remove 'rm', enable 'en', disable 'di', add 'ad'"
        print "plugin 'p', orchestrator 'o', resource 'r', location 'l', template 't'"
        print "example remove plugin => rmp"






#url = 'http://example.com/some/cookie/setting/url'
#r = requests.get(url)
#print r.cookies['example_cookie_name']
