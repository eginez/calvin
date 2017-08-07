# calvin [![npm package](https://nodei.co/npm/calvin-cljs.png?downloads=true&downloadRank=true&stars=true)](https://nodei.co/npm/calvin-cljs/)

A minimalistic build tool for clojurescript in clojurescript that does not require the jvm.

## Why?
In conjunction with boostrapped clojurescript calvin aims to enable a clojurescript jvm-less development environment

## Current status
1. Repls: calvin boostraps [planck](https://github.com/mfikes/planck) or [lumo](https://github.com/anmonteiro/lumo) repls
with the proper "classpath" as described in a lein project file
2. Dependencies: calvin resolves and prints dependencies specified in lein project files
3. Building: Calvin can now build your cljs project using the lumo build api and your lein-cljsbuild configuration. 
For more information look at the [lumo build api](https://anmonteiro.com/2017/02/compiling-clojurescript-projects-without-the-jvm/)

## Installation

0. Install lumo or planck

1. Download and install via [npm](https://www.npmjs.com/package/calvin-cljs)


## Getting started
Once calvin is installed you can do this in your terminal

    calvin -h

This will print out the help for calvin

### Repls
To start one of the boostrapped repls you can

start a lumo repl

    calvin repl

start a planck repl

    calvin -p planck repl

Any remaining arguments are passed on to lumo/planck, so you can do things like

    calvin repl my_script.cljs
    calvin repl --dumb-terminal
    calvin repl -c src -m my-project.main
    calvin repl --socket-repl 3333

### Dependencies
To discover the dependecies of  a project

    calvin deps

Calvin assumes there is a lein project file in the current directory. It will read such
file and resolve transitive dependencies

### Building
Build will read the `cljsbuild :compiler` options of yout `project.clj` file.
Please note that some compiler options are not supported by the `lumo.build.api`

    calvin build dev


