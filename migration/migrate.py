#!/usr/bin/env python3
"""Migrate the PetClinic relational schema from SQL Server to MongoDB.

Reads the tables owners, pets, visits, types, vets, specialties and vet_specialties from SQL Server
and writes the PetClinic document model expected by the Spring Data MongoDB application:

  owners : {firstName, lastName, address, city, telephone,
            pets: [{_id, name, birthDate, type: {_id, name}, visits: [{_id, visitDate, description}]}]}
  vets   : {firstName, lastName, specialties: [{_id, name}]}
  types  : {_id, name}

Configuration (environment variables):
  MSSQL_URL   mssql://user:password@host:1433/petclinic   (required)
  MONGO_URI   mongodb://localhost:27017/petclinic?replicaSet=rs0   (required, database taken from the path)
  DROP_TARGET "true" to drop the target collections before importing (default: refuse non-empty targets)
  DRY_RUN     "true" to read SQL Server and print the summary without writing to MongoDB

Relational integer ids are replaced by new ObjectIds; the original value is kept in a "legacyId" field so the
migration can be audited and re-run idempotently with DROP_TARGET=true.
"""

import os
import sys
from collections import defaultdict
from datetime import date, datetime, time, timezone
from urllib.parse import unquote, urlparse

import pymssql
from bson import ObjectId
from pymongo import ASCENDING, MongoClient

COLLECTIONS = ("owners", "vets", "types")


def env_flag(name):
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes")


def parse_mssql_url(url):
    parsed = urlparse(url)
    if parsed.scheme not in ("mssql", "sqlserver") or not parsed.hostname or not parsed.path.strip("/"):
        raise ValueError("MSSQL_URL must look like mssql://user:password@host:1433/database")
    return {
        "server": parsed.hostname,
        "port": parsed.port or 1433,
        "user": unquote(parsed.username or ""),
        "password": unquote(parsed.password or ""),
        "database": parsed.path.strip("/"),
    }


def to_datetime(value):
    """SQL Server DATE columns map to datetime.date; MongoDB stores dates as UTC midnight."""
    if value is None:
        return None
    if isinstance(value, datetime):
        value = value.date()
    if isinstance(value, date):
        return datetime.combine(value, time.min, tzinfo=timezone.utc)
    raise TypeError(f"Unsupported date value {value!r}")


def fetch_all(cursor, sql):
    cursor.execute(sql)
    return cursor.fetchall()


def read_sqlserver(conn):
    cursor = conn.cursor(as_dict=True)
    return {
        "types": fetch_all(cursor, "SELECT id, name FROM types ORDER BY id"),
        "specialties": fetch_all(cursor, "SELECT id, name FROM specialties ORDER BY id"),
        "vets": fetch_all(cursor, "SELECT id, first_name, last_name FROM vets ORDER BY id"),
        "vet_specialties": fetch_all(cursor, "SELECT vet_id, specialty_id FROM vet_specialties"),
        "owners": fetch_all(cursor,
                            "SELECT id, first_name, last_name, address, city, telephone FROM owners ORDER BY id"),
        "pets": fetch_all(cursor, "SELECT id, name, birth_date, type_id, owner_id FROM pets ORDER BY id"),
        "visits": fetch_all(cursor, "SELECT id, pet_id, visit_date, description FROM visits ORDER BY id"),
    }


def build_documents(rows):
    """Transform relational rows into the embedded document model."""
    types = {r["id"]: {"_id": ObjectId(), "name": r["name"], "legacyId": r["id"]} for r in rows["types"]}
    embedded_type = {k: {"_id": v["_id"], "name": v["name"]} for k, v in types.items()}

    specialties = {r["id"]: {"_id": ObjectId(), "name": r["name"]} for r in rows["specialties"]}
    specialties_by_vet = defaultdict(list)
    for link in rows["vet_specialties"]:
        specialties_by_vet[link["vet_id"]].append(specialties[link["specialty_id"]])

    vets = [{
        "legacyId": r["id"],
        "firstName": r["first_name"],
        "lastName": r["last_name"],
        "specialties": sorted(specialties_by_vet[r["id"]], key=lambda s: s["name"].lower()),
    } for r in rows["vets"]]

    visits_by_pet = defaultdict(list)
    for r in rows["visits"]:
        visits_by_pet[r["pet_id"]].append({
            "_id": ObjectId(),
            "legacyId": r["id"],
            "visitDate": to_datetime(r["visit_date"]),
            "description": r["description"],
        })

    pets_by_owner = defaultdict(list)
    for r in rows["pets"]:
        if r["type_id"] not in embedded_type:
            raise ValueError(f"Pet {r['id']} references unknown type {r['type_id']}")
        pets_by_owner[r["owner_id"]].append({
            "_id": ObjectId(),
            "legacyId": r["id"],
            "name": r["name"],
            "birthDate": to_datetime(r["birth_date"]),
            "type": embedded_type[r["type_id"]],
            "visits": visits_by_pet.pop(r["id"], []),
        })
    if visits_by_pet:
        raise ValueError(f"Visits reference unknown pets: {sorted(visits_by_pet)}")

    owner_ids = {r["id"] for r in rows["owners"]}
    orphan_owners = set(pets_by_owner) - owner_ids
    if orphan_owners:
        raise ValueError(f"Pets reference unknown owners: {sorted(orphan_owners)}")

    owners = [{
        "legacyId": r["id"],
        "firstName": r["first_name"],
        "lastName": r["last_name"],
        "address": r["address"],
        "city": r["city"],
        "telephone": r["telephone"],
        "pets": pets_by_owner[r["id"]],
    } for r in rows["owners"]]

    return {"owners": owners, "vets": vets, "types": list(types.values())}


def write_mongo(db, documents, drop_target):
    for name in COLLECTIONS:
        if drop_target:
            db[name].drop()
        elif db[name].estimated_document_count() > 0:
            raise RuntimeError(f"Target collection '{name}' is not empty; set DROP_TARGET=true to replace it")
    for name in COLLECTIONS:
        if documents[name]:
            db[name].insert_many(documents[name], ordered=True)
    db.owners.create_index([("lastName", ASCENDING)])
    db.owners.create_index([("pets._id", ASCENDING)])
    db.vets.create_index([("lastName", ASCENDING), ("firstName", ASCENDING)])
    db.types.create_index([("name", ASCENDING)], unique=True)


def summary(documents):
    pets = sum(len(o["pets"]) for o in documents["owners"])
    visits = sum(len(p["visits"]) for o in documents["owners"] for p in o["pets"])
    return (f"{len(documents['owners'])} owners, {pets} pets, {visits} visits, "
            f"{len(documents['vets'])} vets, {len(documents['types'])} pet types")


def main():
    mssql_url = os.environ.get("MSSQL_URL")
    mongo_uri = os.environ.get("MONGO_URI")
    if not mssql_url or not mongo_uri:
        print("MSSQL_URL and MONGO_URI environment variables are required", file=sys.stderr)
        return 2

    with pymssql.connect(**parse_mssql_url(mssql_url)) as conn:
        rows = read_sqlserver(conn)
    documents = build_documents(rows)
    print(f"Read from SQL Server: {summary(documents)}")

    if env_flag("DRY_RUN"):
        print("DRY_RUN=true: nothing written to MongoDB")
        return 0

    with MongoClient(mongo_uri) as client:
        db = client.get_default_database()
        write_mongo(db, documents, env_flag("DROP_TARGET"))
        print(f"Written to MongoDB database '{db.name}': {summary(documents)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
