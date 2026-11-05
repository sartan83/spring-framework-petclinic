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
package org.springframework.samples.petclinic.repository.mongo;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB implementation of {@link PetRepository}: pets are read from and written to their owner document.
 */
@Repository
public class MongoPetRepository implements PetRepository {

    private final MongoTemplate mongoTemplate;
    private final OwnerRepository ownerRepository;

    public MongoPetRepository(MongoTemplate mongoTemplate, OwnerRepository ownerRepository) {
        this.mongoTemplate = mongoTemplate;
        this.ownerRepository = ownerRepository;
    }

    @Override
    public List<PetType> findPetTypes() {
        return mongoTemplate.find(new Query().with(Sort.by("name")), PetType.class);
    }

    @Override
    public Pet findById(String id) {
        return ownerRepository.findByPetsId(id)
            .flatMap(owner -> owner.getPetsInternal().stream().filter(pet -> id.equals(pet.getId())).findFirst())
            .orElse(null);
    }

    @Override
    public void save(Pet pet) {
        Owner owner = pet.getOwner();
        if (owner == null) {
            throw new IllegalArgumentException("Pet " + pet + " must belong to an owner before being saved");
        }
        if (pet.isNew()) {
            pet.setId(ObjectId.get().toHexString());
        }
        // A pet bound from a web form replaces the stored instance but must keep its embedded visits.
        owner.getPetsInternal().removeIf(existing -> {
            boolean replaced = existing != pet && pet.getId().equals(existing.getId());
            if (replaced && pet.getVisitsInternal().isEmpty()) {
                existing.getVisitsInternal().forEach(pet::addVisit);
            }
            return replaced;
        });
        owner.getPetsInternal().add(pet);
        ownerRepository.save(owner);
    }

}
