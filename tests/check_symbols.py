#!/usr/bin/env python3
"""Checks that symbol_table.txt is consistent with tree.xml.

usage: check_symbols.py <tree.xml> <symbol_table.txt>
  - every new name in the table is unique
  - every user-defined name leaf (#...) in the tree is bound to exactly one entity
  - every binding points at a name leaf, and at an entity with that same user-given name
  - every entity's own declaration leaf is bound to it
Exits 0 if all checks pass, otherwise prints the problems and exits 1.
"""
import sys
import xml.etree.ElementTree as ET

tree_path, table_path = sys.argv[1], sys.argv[2]
problems = []

name_leaves = {}  # node id -> name
for el in ET.parse(tree_path).getroot():
    if el.tag == "LEAF" and el.findtext("CONTENTS").startswith("#"):
        name_leaves[int(el.findtext("UNID"))] = el.findtext("CONTENTS")

with open(table_path, encoding="utf-8") as f:
    lines = f.read().splitlines()
split = lines.index("NAME OCCURRENCES (tree node id -> new name)")
entries = {}      # new name -> (name, kind, line:col)
for row in lines[3:split]:
    if row.strip():
        new, name, kind, _ret, _lvl, _scope, decl, _type = row.split()
        if new in entries:
            problems.append(f"new name {new} is used for two entities")
        entries[new] = (name, kind, decl)
bindings = {}     # node id -> new name
for row in lines[split + 2:]:
    if row.strip():
        uid, name, new = row.split()
        bindings[int(uid)] = new
        if int(uid) not in name_leaves:
            problems.append(f"binding for node {uid}, which is not a name leaf in the tree")
        elif name_leaves[int(uid)] != name:
            problems.append(f"node {uid} is {name_leaves[int(uid)]} in the tree but {name} in the table")
        if new not in entries:
            problems.append(f"node {uid} is bound to unknown entity {new}")
        elif entries[new][0] != name:
            problems.append(f"node {uid} ({name}) is bound to {new}, which is named {entries[new][0]}")

for uid, name in name_leaves.items():
    if uid not in bindings:
        problems.append(f"name leaf {uid} ({name}) is not bound to any entity")
for new in entries:
    if new not in bindings.values():
        problems.append(f"entity {new} has no bound occurrence (not even its declaration)")

for p in problems:
    print(p)
sys.exit(1 if problems else 0)
