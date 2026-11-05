# Migrating PetClinic data from SQL Server to MongoDB

`migrate.py` copies an existing PetClinic relational database hosted on SQL Server into the document model
used by the Spring Data MongoDB version of the application.

## Mapping

| SQL Server table(s)                     | MongoDB                                                        |
|-----------------------------------------|----------------------------------------------------------------|
| `types`                                 | `types` collection (`{_id, name}`)                             |
| `owners`                                | `owners` collection                                            |
| `pets` (+ `types`)                      | `owners.pets[]` embedded, pet type copied into `pets[].type`   |
| `visits`                                | `owners.pets[].visits[]` embedded                              |
| `vets`                                  | `vets` collection                                              |
| `specialties` + `vet_specialties`       | `vets.specialties[]` embedded                                  |

Resulting documents:

```js
// owners
{ _id: ObjectId, legacyId: 6, firstName: "Jean", lastName: "Coleman", address: "105 N. Lake St.",
  city: "Monona", telephone: "6085552654",
  pets: [ { _id: ObjectId, legacyId: 7, name: "Samantha", birthDate: ISODate("2012-09-04"),
            type: { _id: ObjectId, name: "cat" },
            visits: [ { _id: ObjectId, legacyId: 1, visitDate: ISODate("2013-01-01"), description: "rabies shot" } ] } ] }
// vets
{ _id: ObjectId, legacyId: 2, firstName: "Helen", lastName: "Leary", specialties: [ { _id: ObjectId, name: "radiology" } ] }
```

Integer primary keys are replaced by new `ObjectId`s (the application uses their hexadecimal String form in
URLs such as `/owners/6560f1a2b3c4d5e6f7a8b9c0`). The original key is preserved in `legacyId` for auditing.
Dates are stored as UTC midnight `Date` values, exactly like the seed script `src/main/resources/mongo/init-data.js`.

## Prerequisites

- Python 3.9+
- Network access to SQL Server (TDS, default port 1433) and to MongoDB 8.x
- MongoDB must be the target database of the application (replica set recommended, as the application
  uses `MongoTransactionManager`)

```bash
cd migration
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
```

## Usage

| Variable      | Required | Description                                                                  |
|---------------|----------|------------------------------------------------------------------------------|
| `MSSQL_URL`   | yes      | `mssql://user:password@host:1433/database` (URL-encode special characters)   |
| `MONGO_URI`   | yes      | MongoDB connection string; the database is taken from the path               |
| `DROP_TARGET` | no       | `true` drops `owners`, `vets`, `types` before import (otherwise non-empty targets abort the run) |
| `DRY_RUN`     | no       | `true` reads SQL Server and prints the summary without writing to MongoDB    |

```bash
# 1. dry run: check connectivity and row counts
DRY_RUN=true \
MSSQL_URL='mssql://sa:MyP%40ssw0rd@sqlserver.example.com:1433/petclinic' \
MONGO_URI='mongodb://localhost:27017/petclinic?replicaSet=rs0' \
python3 migrate.py

# 2. real import (into the MongoDB started by docker compose, replacing the seed data)
DROP_TARGET=true \
MSSQL_URL='mssql://sa:MyP%40ssw0rd@sqlserver.example.com:1433/petclinic' \
MONGO_URI='mongodb://localhost:27017/petclinic?replicaSet=rs0' \
python3 migrate.py
```

The script also creates the indexes used by the application (`owners.lastName`, `owners.pets._id`,
`vets.lastName/firstName`, unique `types.name`).

## Verification

```bash
mongosh 'mongodb://localhost:27017/petclinic' --eval '
  printjson({
    owners: db.owners.countDocuments(),
    pets:   db.owners.aggregate([{$unwind: "$pets"}, {$count: "n"}]).toArray(),
    visits: db.owners.aggregate([{$unwind: "$pets"}, {$unwind: "$pets.visits"}, {$count: "n"}]).toArray(),
    vets:   db.vets.countDocuments(),
    types:  db.types.countDocuments()
  })'
```

Compare the numbers with `SELECT COUNT(*)` on the corresponding SQL Server tables, then start the
application against the migrated database (`MONGODB_URI=... docker compose up petclinic`).
