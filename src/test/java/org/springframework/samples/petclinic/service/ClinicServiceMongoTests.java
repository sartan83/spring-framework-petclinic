/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Integration test of the {@link ClinicService} against a real MongoDB 8 single-node replica set started with
 * Testcontainers and populated with the {@code mongo/init-data.js} seed script used by docker-compose.
 */
@Testcontainers
@SpringJUnitConfig(locations = {"classpath:spring/business-config.xml"})
class ClinicServiceMongoTests extends AbstractClinicServiceTests {

    private static final String MONGO_IMAGE = System.getProperty("mongodb.image", "mongo:8.0");

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer(MONGO_IMAGE).withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb.uri", () -> mongo.getReplicaSetUrl("petclinic"));
    }

    @BeforeAll
    static void seedDatabase() throws Exception {
        mongo.copyFileToContainer(MountableFile.forClasspathResource("mongo/init-data.js"), "/tmp/init-data.js");
        ExecResult result = mongo.execInContainer("mongosh", "--quiet", "mongodb://localhost:27017/petclinic",
            "/tmp/init-data.js");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

}
