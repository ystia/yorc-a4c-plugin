#!/usr/bin/env python
from os import environ

# Set output val using two different ways
myVar1="Resolved {0}".format(var1)
myVar2="Resolved {0}".format(environ['var2'])

