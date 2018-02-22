# Yorc Samples TOSCA components

This directory contains TOSCA components used to test or demonstrates Yorc features.

For setting up Alien4Cloud and Yorc please refer to our documentation.

You can import them using [the git import feature of Alien 4 Cloud](http://alien4cloud.github.io/#/documentation/2.0.0/getting_started/new_getting_started.html)
or alternatively you can zip all components with the following command:

```bash
find . -type f -name types.yaml -exec bash -c "cd \$(dirname {}); zip -r types.zip ." \;

# find zip files to import in A4C
find . -type f -name types.zip
```

Using this methods you have to respect the dependency tree between components.

That means that public `pub` components should be imported first the concrete implementations and finally topologies.  


