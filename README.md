<!-- TOC -->
* [Java Developer Training Modules](#java-developer-training-modules)
* [Modules](#modules)
* [Terms of Use](#terms-of-use)
* [Compatibility](#compatibility)
* [Setup](#setup)
  * [Prerequisites](#prerequisites)
  * [Install the JDK](#install-the-jdk)
  * [Database Setup](#database-setup)
  * [Building](#building)
    * [Clone the project](#clone-the-project)
    * [Build the artifact](#build-the-artifact)
* [Running](#running)
<!-- TOC -->

# Java Developer Training Modules

Spring Boot code samples referenced from the 
[Java Developer Training]([Spring](https://docs.google.com/presentation/d/1-uUnbs9TPBW-ISPGW4D6ukms5Kogelh9vK-2QOGLt_k/edit?usp=sharing))
presentation deck.

# Modules

- [training-modules-ch1-transactions](training-modules-ch1-transactions/README.md)
- [training-modules-ch2-contention](training-modules-ch2-contention/README.md)
- [training-modules-ch3-performance](training-modules-ch3-performance/README.md)
- [training-modules-common](training-modules-common/README.md)
- [training-modules-domain](training-modules-domain/README.md)

# Terms of Use

This tool is not supported by Cockroach Labs. Use of this tool is entirely at your
own risk and Cockroach Labs makes no guarantees or warranties about its operation.

See [MIT](LICENSE.txt) for terms and conditions.

# Compatibility

- MacOS
- Linux
- JDK 21+ (LTS)
- CockroachDB v23+

# Setup

Things you need to build and run the modules locally.

## Prerequisites

- Java 21+ JDK
    - https://openjdk.org/projects/jdk/21/
    - https://www.oracle.com/java/technologies/downloads/#java21
- Git
    - https://git-scm.com/downloads/mac

## Install the JDK

MacOS (using sdkman):

    curl -s "https://get.sdkman.io" | bash
    sdk list java
    sdk install java 21.0 (use TAB to pick edition)  

Ubuntu:

    sudo apt-get install openjdk-21-jdk

## Database Setup

See [start a local cluster](https://www.cockroachlabs.com/docs/v24.2/start-a-local-cluster)
for setup instructions. You can also use CockroachDB Cloud (basic, standard or advanced).
A valid license is needed for some of the chapters that use enterprise features like 
follower reads and CDC.

## Building

### Clone the project

    git clone git@github.com:cockroachlabs-field/training-modules.git && cd training-modules

### Build the artifact

    chmod +x mvnw
    ./mvnw clean install

# Running

Pick the training chapter you want to run the tests in, for example.

    cd training-modules-ch1-transactions

Then run the test starter script which will present a menu of options:

    ./run-test.sh

If you need to connect to a CockroachCloud cluster or non-local cluster, you can edit the
`./run-server.sh` file accordingly.

---

That is all, carry on!