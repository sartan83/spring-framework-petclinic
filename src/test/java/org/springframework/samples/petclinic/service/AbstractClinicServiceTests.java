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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p> Base class for {@link ClinicService} integration tests. </p> <p> Subclasses should specify Spring context
 * configuration using {@link ContextConfiguration @ContextConfiguration} annotation </p> <p>
 * AbstractclinicServiceTests and its subclasses benefit from the following services provided by the Spring
 * TestContext Framework: </p> <ul> <li><strong>Spring IoC container caching</strong> which spares us unnecessary set up
 * time between test execution.</li> <li><strong>Dependency Injection</strong> of test fixture instances, meaning that
 * we don't need to perform application context lookups. See the use of {@link Autowired @Autowired} on the <code>{@link
 * AbstractClinicServiceTests#clinicService clinicService}</code> instance variable, which uses autowiring <em>by
 * type</em>. <li><strong>Transaction management</strong>, meaning each test method is executed in its own transaction,
 * which is automatically rolled back by default. Thus, even if tests insert or otherwise change database state, there
 * is no need for a teardown or cleanup script. <li> An {@link org.springframework.context.ApplicationContext
 * ApplicationContext} is also inherited and can be used for explicit bean lookup if necessary. </li> </ul>
 *
 * @author Ken Krebs
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 */
abstract class AbstractClinicServiceTests {

    @Autowired
    protected ClinicService clinicService;

    protected Owner findOwner(String lastName, String firstName) {
        return this.clinicService.findOwnerByLastName(lastName).stream()
            .filter(owner -> owner.getFirstName().equals(firstName))
            .findFirst()
            .orElseThrow();
    }

    protected Pet findSamantha() {
        Pet samantha = findOwner("Coleman", "Jean").getPet("Samantha");
        return this.clinicService.findPetById(samantha.getId());
    }

    protected PetType findPetType(String name) {
        return this.clinicService.findPetTypes().stream()
            .filter(type -> type.getName().equals(name))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void shouldFindOwnersByLastName() {
        Collection<Owner> owners = this.clinicService.findOwnerByLastName("Davis");
        assertThat(owners).hasSize(2);

        owners = this.clinicService.findOwnerByLastName("Daviss");
        assertThat(owners).isEmpty();
    }

    @Test
    void shouldFindOwnersByLastNamePrefixIgnoringCase() {
        assertThat(this.clinicService.findOwnerByLastName("dav")).extracting(Owner::getLastName)
            .containsOnly("Davis").hasSize(2);
        assertThat(this.clinicService.findOwnerByLastName("")).hasSize(10);
    }

    @Test
    void shouldFindSingleOwnerWithPet() {
        String id = findOwner("Franklin", "George").getId();
        Owner owner = this.clinicService.findOwnerById(id);
        assertThat(owner.getId()).isEqualTo(id);
        assertThat(owner.getLastName()).startsWith("Franklin");
        assertThat(owner.getPets()).hasSize(1);
        assertThat(owner.getPets().get(0).getType()).isNotNull();
        assertThat(owner.getPets().get(0).getType().getName()).isEqualTo("cat");
        assertThat(owner.getPets().get(0).getOwner()).isSameAs(owner);
    }

    @Test
    void shouldReturnNullForUnknownIds() {
        assertThat(this.clinicService.findOwnerById("000000000000000000000000")).isNull();
        assertThat(this.clinicService.findPetById("000000000000000000000000")).isNull();
    }

    @Test
    @Transactional
    public void shouldInsertOwner() {
        Collection<Owner> owners = this.clinicService.findOwnerByLastName("Schultz");
        int found = owners.size();

        Owner owner = new Owner();
        owner.setFirstName("Sam");
        owner.setLastName("Schultz");
        owner.setAddress("4, Evans Street");
        owner.setCity("Wollongong");
        owner.setTelephone("4444444444");
        this.clinicService.saveOwner(owner);
        assertThat(owner.getId()).isNotBlank();

        owners = this.clinicService.findOwnerByLastName("Schultz");
        assertThat(owners).hasSize(found + 1);
    }

    @Test
    @Transactional
    void shouldUpdateOwner() {
        String id = findOwner("Franklin", "George").getId();
        Owner owner = this.clinicService.findOwnerById(id);
        String oldLastName = owner.getLastName();
        String newLastName = oldLastName + "X";

        owner.setLastName(newLastName);
        this.clinicService.saveOwner(owner);

        // retrieving new name from database
        owner = this.clinicService.findOwnerById(id);
        assertThat(owner.getLastName()).isEqualTo(newLastName);
    }

    @Test
    @Transactional
    void shouldKeepPetsWhenUpdatingOwnerFromForm() {
        Owner stored = findOwner("Coleman", "Jean");

        // the owner edit form binds a new Owner instance without pets
        Owner fromForm = new Owner();
        fromForm.setId(stored.getId());
        fromForm.setFirstName("Jeanne");
        fromForm.setLastName(stored.getLastName());
        fromForm.setAddress(stored.getAddress());
        fromForm.setCity(stored.getCity());
        fromForm.setTelephone(stored.getTelephone());
        this.clinicService.saveOwner(fromForm);

        Owner owner = this.clinicService.findOwnerById(stored.getId());
        assertThat(owner.getFirstName()).isEqualTo("Jeanne");
        assertThat(owner.getPets()).extracting(Pet::getName).containsExactly("Max", "Samantha");
    }

    @Test
    void shouldFindPetWithCorrectId() {
        Pet samantha = findSamantha();
        Pet pet = this.clinicService.findPetById(samantha.getId());
        assertThat(pet.getName()).startsWith("Samantha");
        assertThat(pet.getOwner().getFirstName()).isEqualTo("Jean");
    }

    @Test
    void shouldFindAllPetTypes() {
        Collection<PetType> petTypes = this.clinicService.findPetTypes();
        assertThat(petTypes).extracting(PetType::getName)
            .containsExactly("bird", "cat", "dog", "hamster", "lizard", "snake");
        assertThat(petTypes).allSatisfy(type -> assertThat(type.getId()).isNotBlank());
    }

    @Test
    @Transactional
    public void shouldInsertPetIntoDatabaseAndGenerateId() {
        String ownerId = findOwner("Coleman", "Jean").getId();
        Owner owner6 = this.clinicService.findOwnerById(ownerId);
        int found = owner6.getPets().size();

        Pet pet = new Pet();
        // the creation form posts an empty hidden "id" field
        pet.setId("");
        pet.setName("bowser");
        pet.setType(findPetType("dog"));
        pet.setBirthDate(LocalDate.now());
        owner6.addPet(pet);
        assertThat(owner6.getPets()).hasSize(found + 1);

        this.clinicService.savePet(pet);
        this.clinicService.saveOwner(owner6);

        owner6 = this.clinicService.findOwnerById(ownerId);
        assertThat(owner6.getPets()).hasSize(found + 1);
        // checks that id has been generated
        assertThat(pet.getId()).isNotNull();
        Pet bowser = this.clinicService.findPetById(pet.getId());
        assertThat(bowser.getName()).isEqualTo("bowser");
        assertThat(bowser.getType().getName()).isEqualTo("dog");
        assertThat(bowser.getOwner().getId()).isEqualTo(ownerId);
    }

    @Test
    @Transactional
    public void shouldUpdatePetName() throws Exception {
        Pet pet7 = findSamantha();
        String oldName = pet7.getName();

        String newName = oldName + "X";
        pet7.setName(newName);
        this.clinicService.savePet(pet7);

        pet7 = this.clinicService.findPetById(pet7.getId());
        assertThat(pet7.getName()).isEqualTo(newName);
        assertThat(pet7.getVisits()).hasSize(2);
    }

    @Test
    @Transactional
    void shouldKeepVisitsWhenUpdatingPetFromForm() {
        Pet stored = findSamantha();
        Owner owner = stored.getOwner();

        // the pet edit form binds a new Pet instance without visits
        Pet fromForm = new Pet();
        fromForm.setId(stored.getId());
        fromForm.setName("Sam");
        fromForm.setBirthDate(stored.getBirthDate());
        fromForm.setType(findPetType("dog"));
        owner.addPet(fromForm);
        this.clinicService.savePet(fromForm);

        Pet pet = this.clinicService.findPetById(stored.getId());
        assertThat(pet.getName()).isEqualTo("Sam");
        assertThat(pet.getType().getName()).isEqualTo("dog");
        assertThat(pet.getVisits()).hasSize(2);
        assertThat(this.clinicService.findOwnerById(owner.getId()).getPets()).hasSize(2);
    }

    @Test
    void shouldFindVets() {
        List<Vet> vets = List.copyOf(this.clinicService.findVets());
        assertThat(vets).extracting(Vet::getLastName)
            .containsExactly("Carter", "Douglas", "Jenkins", "Leary", "Ortega", "Stevens");

        Vet vet = vets.get(1);
        assertThat(vet.getId()).isNotBlank();
        assertThat(vet.getLastName()).isEqualTo("Douglas");
        assertThat(vet.getNrOfSpecialties()).isEqualTo(2);
        assertThat(vet.getSpecialties().get(0).getName()).isEqualTo("dentistry");
        assertThat(vet.getSpecialties().get(1).getName()).isEqualTo("surgery");
    }

    @Test
    @Transactional
    public void shouldAddNewVisitForPet() {
        Pet pet7 = findSamantha();
        int found = pet7.getVisits().size();
        Visit visit = new Visit();
        pet7.addVisit(visit);
        visit.setDescription("test");
        this.clinicService.saveVisit(visit);
        this.clinicService.savePet(pet7);

        pet7 = this.clinicService.findPetById(pet7.getId());
        assertThat(pet7.getVisits()).hasSize(found + 1);
        assertThat(visit.getId()).isNotNull();
        assertThat(pet7.getVisits()).extracting(Visit::getId).contains(visit.getId());
    }

    @Test
    void shouldFindVisitsByPetId() throws Exception {
        String petId = findSamantha().getId();
        Collection<Visit> visits = this.clinicService.findVisitsByPetId(petId);
        assertThat(visits).hasSize(2);
        Visit[] visitArr = visits.toArray(new Visit[visits.size()]);
        assertThat(visitArr[0].getPet()).isNotNull();
        assertThat(visitArr[0].getDate()).isEqualTo(LocalDate.of(2013, 1, 4));
        assertThat(visitArr[0].getDescription()).isEqualTo("spayed");
        assertThat(visitArr[0].getPet().getId()).isEqualTo(petId);
    }

}
