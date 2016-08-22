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

Main.adl(Main.run("", t))
Main.adr(Main.run("", t))
Main.adt(Main.run("", t))
Main.ada(Main.run("", t))
Main.deploy(Main.run("", t))
Main.undeploy(Main.run("", t))
