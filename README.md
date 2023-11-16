# glone
CLI for quickly cloning Gitlab projects

## Quickstart
```shell
# glone uses your current work dir as the base for cloning, each project will be cloned into a directory structure representing the gitlab namespace
# Use -Pdir to specify a different base directory, this must exist before running glone.
./gradlew run --args="GROUP/SUBGROUP_A GROUP/SUBGROUP_B" -Pdir=/path/to/clone/to
```

## Build GraalVM native image
```shell
./gradlew nativeCompile
```