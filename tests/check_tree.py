#!/usr/bin/env python3
"""Checks that tree.xml is a well-formed, consistent syntax tree for the given SPL source.

usage: check_tree.py <tree.xml> <source.txt>
Exits 0 if all checks pass, otherwise prints the problems and exits 1.
"""
import sys
import xml.etree.ElementTree as ET

tree_path, src_path = sys.argv[1], sys.argv[2]
problems = []

try:
    root = ET.parse(tree_path).getroot()
except ET.ParseError as e:
    print(f"tree.xml is not well-formed XML: {e}")
    sys.exit(1)

if root.tag != "SYNTAXTREE":
    problems.append(f"root element is <{root.tag}>, expected <SYNTAXTREE>")

nodes = {}   # id -> (tag, contents, children, parent)
order = []   # ids in document order
for el in root:
    uid = int(el.findtext("UNID"))
    contents = el.findtext("CONTENTS")
    ch = el.find("CHILDREN")
    children = [int(c.text) for c in ch.findall("ID")] if ch is not None else None
    parent = el.findtext("PARENT")
    nodes[uid] = (el.tag, contents, children, int(parent) if parent else None)
    order.append(uid)

# Node kinds carry the right fields
roots = [u for u, n in nodes.items() if n[0] == "ROOT"]
if len(roots) != 1:
    problems.append(f"expected exactly one ROOT, found {len(roots)}")
for u, (tag, contents, children, parent) in nodes.items():
    if tag == "ROOT" and (parent is not None or children is None):
        problems.append(f"ROOT {u} must have CHILDREN and no PARENT")
    if tag == "INNER" and (parent is None or children is None):
        problems.append(f"INNER {u} must have CHILDREN and PARENT")
    if tag == "LEAF" and (parent is None or children is not None):
        problems.append(f"LEAF {u} must have PARENT and no CHILDREN")

# IDs are unique, 1..n, and listed in order
if order != list(range(1, len(order) + 1)):
    problems.append("UNIDs are not 1..n in document order")

# Parent and child links agree in both directions
for u, (tag, _, children, parent) in nodes.items():
    for c in children or []:
        if c not in nodes:
            problems.append(f"node {u} lists missing child {c}")
        elif nodes[c][3] != u:
            problems.append(f"node {c} is a child of {u} but its PARENT is {nodes[c][3]}")
    if parent is not None and (parent not in nodes or u not in (nodes[parent][2] or [])):
        problems.append(f"node {u} claims PARENT {parent}, which does not list it as a child")

# Pre-order walk of the tree reproduces the document order,
# and its leaves spell out exactly the tokens of the source file
if roots and not problems:
    walk, leaves, stack = [], [], [roots[0]]
    while stack:
        u = stack.pop()
        walk.append(u)
        tag, contents, children, _ = nodes[u]
        if tag == "LEAF":
            leaves.append(contents)
        stack.extend(reversed(children or []))
    if walk != order:
        problems.append("nodes are not listed in pre-order")
    with open(src_path, encoding="utf-8") as f:
        tokens = f.read().split()
    if leaves != tokens:
        problems.append(f"leaves do not match source tokens\n    leaves: {leaves}\n    tokens: {tokens}")
    if nodes[roots[0]][1] != "SPL_PROG":
        problems.append(f"ROOT contents is {nodes[roots[0]][1]!r}, expected 'SPL_PROG'")

for p in problems:
    print(p)
sys.exit(1 if problems else 0)
