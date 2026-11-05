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
package org.springframework.samples.petclinic.model;

import org.springframework.data.annotation.Id;

/**
 * Simple JavaBean domain object with an id property. Used as a base class for objects needing this property.
 * <p>
 * The id is a MongoDB ObjectId rendered as a hexadecimal String. Top-level documents get it from MongoDB on insert,
 * embedded documents (pets, visits) get it assigned by the repository layer.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 */
public class BaseEntity {

    @Id
    protected String id;

    public String getId() {
        return id;
    }

    /**
     * Blank values (e.g. the empty hidden {@code id} field of a creation form) are stored as {@code null},
     * so that the entity is considered new and gets a generated ObjectId.
     */
    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? null : id;
    }

    public boolean isNew() {
        return this.id == null;
    }

}
