# Python component sample

This sample illustrates how to write a component lifecycle implementation in Python.

## Input parameters

This sample demonstrates how input parameters could be retrieved:

Given the following TOSCA operation definition:
```yaml
    interfaces:
      Standard:
        create:
          inputs:
            var1: {get_property: [SELF, outputVar1]}
            var2: {get_property: [SELF, outputVar2]}
          implementation: scripts/create.py
```

The `create.py` script could retrieve `var1` and `var2` either directly or as an environment variable:

```python
myVar1="Resolved {0}".format(var1)
myVar2="Resolved {0}".format(environ['var2'])
```

## Operation outputs

Operation outputs should be defined in TOSCA as an attribute of the type that define the operation:

```yaml

node_types:
  org.ystia.yorc.samples.python.Component:
    attributes:
      resolvedOutput1: { get_operation_output: [SELF, Standard, create, myVar1]}
      resolvedOutput2: { get_operation_output: [SELF, Standard, create, myVar2]}
    interfaces:
      Standard:
        create:
          inputs:
            var1: {get_property: [SELF, outputVar1]}
            var2: {get_property: [SELF, outputVar2]}
          implementation: scripts/create.py
```

In your Python script you just have to set a global variable or a variable in the script root context to expose it:
 
```python
myVar1="Resolved {0}".format(var1)
myVar2="Resolved {0}".format(environ['var2'])
```

## Outputs & logging

Every message printed to Stdout or Stderr will appear in Yorc/Alien logs.
