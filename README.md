# glone
CLI for quickly cloning Gitlab projects

## Quickstart
```shell
# glone uses your current work dir as the base for cloning, each project will be cloned into a directory structure representing the gitlab namespace
# Use -Pdir to specify a different base directory, this must exist before running glone.
./gradlew run --args="GROUP_A GROUP_B/SUBGROUP_B" -Pdir=/path/to/clone/to
```

## Build GraalVM native image
```shell
./gradlew nativeCompile
```
The native image will be in `build/native/nativeCompile/glone`. You can copy this to somewhere on your path, e.g. `cp build/native/nativeCompile/glone /usr/local/bin`

## Usage
```shell
glone GROUP_A GROUP_B/SUBGROUP_B
```
This will clone all projects in the specified groups and subgroups into a directory structure representing the gitlab namespace, relative to your current working directory.

### From a script
```shell
#! /usr/bin/env sh

#export GITLAB_HOST=gitlab.acme.io

CURRENT_DIR=$( pwd )
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd "$SCRIPT_DIR"

glone GROUP_A GROUP_B/SUBGROUP_B

cd "$CURRENT_DIR"
```