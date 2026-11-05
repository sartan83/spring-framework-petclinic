// PetClinic seed data for MongoDB (equivalent to the former db/*/data.sql scripts).
//
// Usage: mongosh "mongodb://localhost:27017/petclinic" src/main/resources/mongo/init-data.js
//
// Document model:
//   owners : owner fields + embedded pets[] (each pet embeds its type and visits[])
//   vets   : vet fields + embedded specialties[]
//   types  : pet types lookup collection
// The script is idempotent: it does nothing when the owners collection already contains data.

if (db.owners.countDocuments() > 0) {
    print(`PetClinic seed skipped: database '${db.getName()}' already contains owners`);
    quit(0);
}

const day = (isoDate) => ISODate(`${isoDate}T00:00:00Z`);

const specialties = {};
for (const name of ['radiology', 'surgery', 'dentistry']) {
    specialties[name] = { _id: new ObjectId(), name: name };
}

const vet = (firstName, lastName, ...specialtyNames) => ({
    firstName: firstName,
    lastName: lastName,
    specialties: specialtyNames.map((name) => specialties[name])
});

db.vets.insertMany([
    vet('James', 'Carter'),
    vet('Helen', 'Leary', 'radiology'),
    vet('Linda', 'Douglas', 'surgery', 'dentistry'),
    vet('Rafael', 'Ortega', 'surgery'),
    vet('Henry', 'Stevens', 'radiology'),
    vet('Sharon', 'Jenkins')
]);

const types = {};
for (const name of ['cat', 'dog', 'lizard', 'snake', 'bird', 'hamster']) {
    types[name] = { _id: new ObjectId(), name: name };
}
db.types.insertMany(Object.values(types));

const visit = (date, description) => ({ _id: new ObjectId(), visitDate: day(date), description: description });

const pet = (name, birthDate, type, visits = []) => ({
    _id: new ObjectId(),
    name: name,
    birthDate: day(birthDate),
    type: types[type],
    visits: visits
});

const owner = (firstName, lastName, address, city, telephone, pets = []) => ({
    firstName: firstName,
    lastName: lastName,
    address: address,
    city: city,
    telephone: telephone,
    pets: pets
});

db.owners.insertMany([
    owner('George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023', [
        pet('Leo', '2010-09-07', 'cat')
    ]),
    owner('Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749', [
        pet('Basil', '2012-08-06', 'hamster')
    ]),
    owner('Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763', [
        pet('Rosy', '2011-04-17', 'dog'),
        pet('Jewel', '2010-03-07', 'dog')
    ]),
    owner('Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198', [
        pet('Iggy', '2010-11-30', 'lizard')
    ]),
    owner('Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765', [
        pet('George', '2010-01-20', 'snake')
    ]),
    owner('Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654', [
        pet('Samantha', '2012-09-04', 'cat', [
            visit('2013-01-01', 'rabies shot'),
            visit('2013-01-04', 'spayed')
        ]),
        pet('Max', '2012-09-04', 'cat', [
            visit('2013-01-02', 'rabies shot'),
            visit('2013-01-03', 'neutered')
        ])
    ]),
    owner('Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387', [
        pet('Lucky', '2011-08-06', 'bird')
    ]),
    owner('Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683', [
        pet('Mulligan', '2007-02-24', 'dog')
    ]),
    owner('David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435', [
        pet('Freddy', '2010-03-09', 'bird')
    ]),
    owner('Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487', [
        pet('Lucky', '2010-06-24', 'dog'),
        pet('Sly', '2012-06-08', 'cat')
    ])
]);

db.owners.createIndex({ lastName: 1 });
db.owners.createIndex({ 'pets._id': 1 });
db.vets.createIndex({ lastName: 1, firstName: 1 });
db.types.createIndex({ name: 1 }, { unique: true });

print(`PetClinic seed completed: ${db.owners.countDocuments()} owners, ${db.vets.countDocuments()} vets, ${db.types.countDocuments()} pet types`);
