# Consul sample

This component is used to demonstrate several Yorc features. 
It take the excellent [Consul tool](https://www.consul.io) as example for modeling
in TOSCA and deploying a real client/server software with Yorc.

## Implement TOSCA lifecycle operations using Ansible

In this project concrete component lifecycle implementation are done using Ansible playbooks.
It uses several Ansible features like Jinja2 templating or role importing

### Restrictions

Your playbooks should apply to the `all` hosts group. Yorc handles setting up this group with the right
actual hosts.

```yaml
- name: Install consul
  hosts: all
  tasks:
```

### Accessing TOSCA operation input parameters

Input parameters are accessible has playbook variables and so could be used in Jinja2 templating
or in `when` clauses.

As an example given the following TOSCA (some parts omitted for brevity):

```yaml
node_types:
  org.ystia.yorc.samples.consul.linux.ansible.nodes.ConsulServer:
    interfaces:
      Standard:
        inputs:
          INSTALL_DIR: { get_property: [SELF, install_dir] }
          CONFIG_DIR: { get_property: [SELF, config_dir] }
          MODE: { get_property: [SELF, mode] }
          DATA_DIR: { get_property: [SELF, data_dir] }
        create:
          description: Consul installation step
          inputs:
            INSTALL_DNSMASQ: { get_property: [SELF, install_dnsmasq] }
            DOMAIN: { get_property: [SELF, domain] }
            DOWNLOAD_URL: { get_property: [SELF, download_url] }
          implementation: playbooks/consul_install.yaml
```

you can use the following in your playbook:

```yaml
    - name: Backup original resolve.conf
      copy:
        src: /etc/resolv.conf
        remote_src: yes
        dest: /etc/resolv.conf.ori
      when: INSTALL_DNSMASQ == "true"
      
    - name: create Consul user
      user:
        name: consul
        system: true
        group: consul
        home: "{{DATA_DIR}}"
```

or in your templates:

```jinja2
{
  "domain": "{{ DOMAIN }}",
  "data_dir": "{{ DATA_DIR }}",
  "client_addr": "0.0.0.0",
  "advertise_addr": "{{IP_ADDRESS}}"
}
```


### Operation outputs 

Operation outputs should be defined in TOSCA as an attribute of the type that define the operation:

```yaml
node_types:
  org.ystia.yorc.samples.consul.linux.ansible.nodes.ConsulServer:
    attributes:
      outputval: { get_operation_output: [SELF, Standard, create, myVar1] }
    interfaces:
      Standard:
        inputs:
          INSTALL_DIR: { get_property: [SELF, install_dir] }
          CONFIG_DIR: { get_property: [SELF, config_dir] }
          MODE: { get_property: [SELF, mode] }
          DATA_DIR: { get_property: [SELF, data_dir] }
        create:
          description: Consul installation step
          inputs:
            INSTALL_DNSMASQ: { get_property: [SELF, install_dnsmasq] }
            DOMAIN: { get_property: [SELF, domain] }
            DOWNLOAD_URL: { get_property: [SELF, download_url] }
          implementation: playbooks/consul_install.yaml
```

In your playbook you just have to set a fact using the `set_fact` task and Yorc will retrieve it.

```yaml
    - name: Set myVar1 operation output
      set_fact:
        myVar1: "agent"
```

### Using Roles

This component demonstrates how to use 3rd-party roles by using https://galaxy.ansible.com/jriguera/dnsmasq/
to install Dnsmasq

The easiest way to do it is to create a `roles` directory in your playbooks directory and to download
the role using the `ansible-galaxy -p` flag.

Then you can simply use the `import_role` task in your playbook as bellow:

```yaml
    - name: Install Dnsmasq using a 3rd party role
      import_role:
        name: jriguera.dnsmasq
      vars:
        dnsmasq_conf_no_hosts: True
        dnsmasq_conf_servers:
          - [ "/{{DOMAIN}}/127.0.0.1#8600" ]
        dnsmasq_resolvconf: True
        dnsmasq_conf_resolv: "/etc/resolv.conf.ori"
        dnsmasq_conf_log: "DAEMON"
      when: INSTALL_DNSMASQ == "true"
```

## Topologies

A TOSCA topology is a template for a complex application with most of the parameters already defined.

### SimpleClientServer topology

This topology define a simple Consul cluster with one data center.
By default 3 consul servers and 2 consul agent are deployed.

This is the perfect place to begin with this sample. 

### MultiDCWithWAN topology

This is an extension of the SimpleClientServer topology that defines 2 Consul clusters operating
different data centers. Each cluster is operating over its own private network and Consul servers are interconnected
together using the WAN feature over a public network. This is actually done with floating ips on OpenStack.

As Computes are deployed with 2 networks your OS image should be able to automatically setup several NIC at startup.  

### ConsulOnBS topology

This is an extension of the SimpleClientServer topology that uses a block storage and a partition to persist Consul Servers' data.
