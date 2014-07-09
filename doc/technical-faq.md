### how to use compota?
very compuation with compota should form separated sbt project with instructions, datasets, compota configuration

### how to obtain of such project?

install nisperoCLI:

* install cs: `curl https://raw.githubusercontent.com/n8han/conscript/master/setup.sh | sh`
* install nispero super cli: `cs ohnosequences/nisperoCLI -b super-cli` or `~/bin/cs ohnosequences/nisperoCLI -b super-cli` or manually

nispero create <template_repository>

e.g.

nispero create ohnosequences/metapasta.g8

[nisperoCLI documentation](https://github.com/ohnosequences/nisperoCLI/blob/master/doc/universal-cli-tool.md)



create project: nispero create ohnosequences/metapasta or ~/bin/nispero create ohnosequences/metapasta
put samples in configuration
publish it: sbt publish
run it: sbt "run run"
