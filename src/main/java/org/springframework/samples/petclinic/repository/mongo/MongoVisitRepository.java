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
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.repository.PetRepository;
import org.springframework.samples.petclinic.repository.VisitRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB implementation of {@link VisitRepository}: visits are embedded in the pet, itself embedded in the owner.
 */
@Repository
public class MongoVisitRepository implements VisitRepository {

    private final PetRepository petRepository;

    public MongoVisitRepository(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @Override
    public void save(Visit visit) {
        Pet pet = visit.getPet();
        if (pet == null) {
            throw new IllegalArgumentException("Visit " + visit + " must belong to a pet before being saved");
        }
        if (visit.isNew()) {
            visit.setId(ObjectId.get().toHexString());
        }
        if (!pet.getVisitsInternal().contains(visit)) {
            pet.addVisit(visit);
        }
        petRepository.save(pet);
    }

    @Override
    public List<Visit> findByPetId(String petId) {
        Pet pet = petRepository.findById(petId);
        return pet == null ? List.of() : pet.getVisits();
    }

}
