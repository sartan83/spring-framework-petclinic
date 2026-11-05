# Spring PetClinic Sample Application

[![Java CI with Maven](https://github.com/spring-petclinic/spring-framework-petclinic/actions/workflows/maven-build.yml/badge.svg)](https://github.com/spring-petclinic/spring-framework-petclinic/actions/workflows/maven-build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=spring-petclinic_spring-framework-petclinic&metric=alert_status)](https://sonarcloud.io/dashboard?id=spring-petclinic_spring-framework-petclinic)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=spring-petclinic_spring-framework-petclinic&metric=coverage)](https://sonarcloud.io/dashboard?id=spring-petclinic_spring-framework-petclinic)

Approved by the Spring team, this repo is a fork of the [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic).
It allows the Spring community to maintain a Petclinic version with a plain old **Spring Framework configuration**
and with a **3-layer architecture** (i.e. presentation --> service --> repository).
The "canonical" implementation is now based on Spring Boot, Thymeleaf and [aggregate-oriented domain]([https://github.com/spring-projects/spring-petclinic/pull/200). 


## Understanding the Spring Petclinic application with a few diagrams

[See the presentation here](http://fr.slideshare.net/AntoineRey/spring-framework-petclinic-sample-application) (2017 update)

## Running petclinic locally

Petclinic runs on **Java 21** and stores its data in **MongoDB 8.x** through Spring Data MongoDB.
`@Transactional` service methods use a `MongoTransactionManager`, so MongoDB must run as a **replica set**
(a single-node replica set is enough).

### With Docker Compose (recommended)
```
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
cd spring-framework-petclinic
docker compose up --build
```

Compose starts three services:

| Service      | Description                                                                                                |
|--------------|------------------------------------------------------------------------------------------------------------|
| `mongo`      | `mongo:8.0` started with `--replSet rs0 --bind_ip_all`, health-checked, data kept in the `mongo-data` volume |
| `mongo-init` | one-shot job: `rs.initiate()` for `rs0`, waits for the primary, then runs [init-data.js](src/main/resources/mongo/init-data.js) (skipped when data already exists) |
| `petclinic`  | the WAR built with Maven/JDK 21 and deployed as `ROOT.war` on Jetty 12 (`jetty:12-jdk21`), with `MONGODB_URI=mongodb://mongo:27017/petclinic?replicaSet=rs0` |

> MongoDB 8.0 refuses to start on Linux kernel 6.19 or newer (`MongoDB cannot start: Linux kernel versions 6.19 and newer has a known incompatibility`, see [SERVER-121912](https://jira.mongodb.org/browse/SERVER-121912)). Run Docker on an older kernel (e.g. Ubuntu 24.04) until a fixed MongoDB release is available.

Use `docker compose down -v` to stop everything and delete the data volume (the seed runs again at the next start).
Behind a corporate proxy of Maven Central, pass it to the build: `MAVEN_MIRROR_URL=https://nexus.example.com/repository/maven-public/ docker compose up --build`.

### With Maven command line
Start a MongoDB replica set (for instance only the Mongo services of the compose file) and run the WAR with the Jetty Maven plugin:
```
docker compose up -d mongo mongo-init
./mvnw jetty:run-war
# For Windows : ./mvnw.cmd jetty:run-war
```

You can then access petclinic here: [http://localhost:8080/](http://localhost:8080/)

<img width="1042" alt="petclinic-screenshot" src="https://cloud.githubusercontent.com/assets/838318/19727082/2aee6d6c-9b8e-11e6-81fe-e889a5ddfded.png">

A short demo of the main flows (veterinarians, owner search, new owner / pet / visit, error page) on the MongoDB version is available in [docs/demo.mp4](docs/demo.mp4).

## In case you find a bug/suggested improvement for Spring Petclinic

Our issue tracker is available here: https://github.com/spring-petclinic/spring-framework-petclinic/issues


## Database configuration

The connection string is the `mongodb.uri` property of [data-access.properties](src/main/resources/spring/data-access.properties).
Its default value `mongodb://localhost:27017/petclinic` comes from the default `MongoDB` Maven profile of the [pom.xml](pom.xml).
At runtime it can be overridden, without rebuilding the WAR, with:

- the `MONGODB_URI` environment variable, e.g. `MONGODB_URI='mongodb://db1:27017/petclinic?replicaSet=rs0'`
- the `-Dmongodb.uri=...` JVM system property

The database name is taken from the path of the connection string.

### Document model

| Collection | Content                                                                                 |
|------------|-----------------------------------------------------------------------------------------|
| `owners`   | owners with their `pets` embedded; each pet embeds its `type` and its `visits`          |
| `vets`     | veterinarians with their `specialties` embedded                                         |
| `types`    | pet types, used by the pet form drop-down                                               |

Identifiers are MongoDB `ObjectId`s exposed as 24-character hexadecimal Strings (e.g. `/owners/6560f1a2b3c4d5e6f7a8b9c0`).
Embedded pets and visits also get an `ObjectId`, so the existing URLs (`/owners/{ownerId}/pets/{petId}/visits/new`) are unchanged.

### Seed data
[src/main/resources/mongo/init-data.js](src/main/resources/mongo/init-data.js) is the MongoDB equivalent of the former `data.sql`
(10 owners, 13 pets, 4 visits, 6 vets, 3 specialties, 6 pet types). It is idempotent and can be run by hand:
```
mongosh 'mongodb://localhost:27017/petclinic?replicaSet=rs0' src/main/resources/mongo/init-data.js
```

### Migrating from SQL Server
The [migration](migration) folder contains a Python script reading the relational tables
(`owners`, `pets`, `visits`, `vets`, `specialties`, `vet_specialties`, `types`) from SQL Server and writing the document model:
```
pip install -r migration/requirements.txt
MSSQL_URL='mssql://sa:secret@sqlserver:1433/petclinic' MONGO_URI='mongodb://localhost:27017/petclinic?replicaSet=rs0' \
  python3 migration/migrate.py
```
See [migration/sqlserver-to-mongo.md](migration/sqlserver-to-mongo.md) for the mapping, the `DRY_RUN` / `DROP_TARGET` options and the verification steps.

## Running the tests

```
./mvnw verify
```

The service-layer integration tests ([ClinicServiceMongoTests](src/test/java/org/springframework/samples/petclinic/service/ClinicServiceMongoTests.java))
start a `mongo:8.0` single-node replica set with [Testcontainers](https://java.testcontainers.org/modules/databases/mongodb/)
and load the same `init-data.js` seed script, so a running Docker daemon is required.
Use `-Dmongodb.image=...` to test against another MongoDB image.

## Compiling the CSS

There is a `petclinic.css` in `src/main/webapp/resources/resources/css`. 
It was generated from the `petclinic.scss` source, combined with the [Bootstrap](https://getbootstrap.com/) library.
If you make changes to the `scss`, or upgrade Bootstrap, you will need to re-compile the CSS resources
using the Maven profile "css", i.e. `./mvnw generate-resources -P css`.

## Working with Petclinic in your IDE

### Prerequisites
The following items should be installed in your system:
* Java 21 or newer (full JDK not a JRE)
* Maven 3.8+ (https://maven.apache.org/install.html)
* git command line tool (https://help.github.com/articles/set-up-git)
* Jetty 12.1+ (EE 11) or Tomcat 11+
* Docker (MongoDB 8 via docker compose, Testcontainers for the integration tests)
* Your prefered IDE 
  * Eclipse with the m2e plugin. Note: when m2e is available, there is an m2 icon in Help -> About dialog. If m2e is not there, just follow the install process here: http://www.eclipse.org/m2e/
  * [Spring Tools Suite](https://spring.io/tools) (STS)
  * IntelliJ IDEA


### Steps:

1) On the command line
```
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
```

2) Inside Eclipse or STS
```
File -> Import -> Maven -> Existing Maven project
```
Then either build on the command line `./mvnw generate-resources` or using the Eclipse launcher (right click on project and `Run As -> Maven install`) to generate the CSS.
Configure a Jetty or a Tomcat web container then deploy the `spring-petclinic.war` file.

3) Inside IntelliJ IDEA

In the main menu, select `File > Open` and select the Petclinic [pom.xml](pom.xml). Click on the `Open` button.

CSS files are generated from the Maven build. You can either build them on the command line `./mvnw generate-resources` 
or right click on the `spring-petclinic` project then `Maven -> Generates sources and Update Folders`.

Go to the `Run -> Edit Configuration` then configure a Tomcat or a Jetty web container. Deploy the `spring-petclinic.war` file.
Run the application by clicking on the `Run` icon.


4) Navigate to Petclinic

Visit [http://localhost:8080](http://localhost:8080) in your browser.


## Working with Petclinic in IntelliJ IDEA

### prerequisites
The following items should be installed in your system:


## Looking for something in particular?

| Java Config |   |
|-------------|---|
| Java config branch | Petclinic uses XML configuration by default. In case you'd like to use Java Config instead, there is a Java Config branch available [here](https://github.com/spring-petclinic/spring-framework-petclinic/tree/javaconfig) |

| Inside the 'Web' layer | Files |
|------------------------|-------|
| Spring MVC - XML integration | [mvc-view-config.xml](src/main/resources/spring/mvc-view-config.xml)  |
| Spring MVC - ContentNegotiatingViewResolver| [mvc-view-config.xml](src/main/resources/spring/mvc-view-config.xml) |
| JSP custom tags | [WEB-INF/tags](src/main/webapp/WEB-INF/tags), [createOrUpdateOwnerForm.jsp](src/main/webapp/WEB-INF/jsp/owners/createOrUpdateOwnerForm.jsp)|
| JavaScript dependencies | [JavaScript libraries are declared as webjars in the pom.xml](pom.xml) |
| Static resources config | [Resource mapping in Spring configuration](/src/main/resources/spring/mvc-core-config.xml#L30) |
| Static resources usage | [htmlHeader.tag](src/main/webapp/WEB-INF/tags/htmlHeader.tag), [footer.tag](src/main/webapp/WEB-INF/tags/footer.tag) |
| Thymeleaf | In the late 2016, the original [Spring Petclinic](https://github.com/spring-projects/spring-petclinic) has moved from JSP to Thymeleaf. |

| 'Service' and 'Repository' layers | Files |
|-----------------------------------|-------|
| Transactions | [business-config.xml](src/main/resources/spring/business-config.xml), [ClinicServiceImpl.java](src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java) |
| Spring Data MongoDB | [datasource-config.xml](src/main/resources/spring/datasource-config.xml), [business-config.xml](src/main/resources/spring/business-config.xml), [mongo folder](src/main/java/org/springframework/samples/petclinic/repository/mongo) |
| Cache | [tools-config.xml](src/main/resources/spring/tools-config.xml), [ClinicServiceImpl.java](src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java) |


## Publishing a Docker image

This application uses [Google Jib]([https://github.com/GoogleContainerTools/jib) to build an optimized Docker image
into the [Docker Hub](https://cloud.docker.com/u/springcommunity/repository/docker/springcommunity/spring-framework-petclinic/)
repository.
The [pom.xml](pom.xml) has been configured to publish the image with a the `springcommunity/spring-framework-petclinic` image name.

Jib containerizes this WAR project by using the official `jetty:12-jdk21` image as a base image.

Build and push the container image of Petclinic to the Docker Hub registry:
```
mvn jib:build
```


## Interesting Spring Petclinic forks

The Spring Petclinic master branch in the main [spring-projects](https://github.com/spring-projects/spring-petclinic)
GitHub org is the "canonical" implementation, currently based on Spring Boot and Thymeleaf.

This [spring-framework-petclinic](https://github.com/spring-petclinic/spring-framework-petclinic) project is one of the [several forks](https://spring-petclinic.github.io/docs/forks.html) 
hosted in a special GitHub org: [spring-petclinic](https://github.com/spring-petclinic).
If you have a special interest in a different technology stack
that could be used to implement the Pet Clinic then please join the community there.


## Interaction with other open source projects

One of the best parts about working on the Spring Petclinic application is that we have the opportunity to work in direct contact with many Open Source projects. We found some bugs/suggested improvements on various topics such as Spring, Spring Data, Bean Validation and even Eclipse! In many cases, they've been fixed/implemented in just a few days.
Here is a list of them:

| Name | Issue |
|------|-------|
| Spring JDBC: simplify usage of NamedParameterJdbcTemplate | [SPR-10256](https://github.com/spring-projects/spring-framework/issues/14889) and [SPR-10257](https://github.com/spring-projects/spring-framework/issues/14890) |
| Bean Validation / Hibernate Validator: simplify Maven dependencies and backward compatibility |[HV-790](https://hibernate.atlassian.net/browse/HV-790) and [HV-792](https://hibernate.atlassian.net/browse/HV-792) |
| Spring Data: provide more flexibility when working with JPQL queries | [DATAJPA-292](https://github.com/spring-projects/spring-data-jpa/issues/704) |
| Dandelion: improves the DandelionFilter for Jetty support | [113](https://github.com/dandelion/dandelion/issues/113) |


# Contributing

The [issue tracker](/issues) is the preferred channel for bug reports, features requests and submitting pull requests.

For pull requests, editor preferences are available in the [editor config](.editorconfig) for easy use in common text editors. Read more and download plugins at <http://editorconfig.org>.




