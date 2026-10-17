#!/usr/bin/env python3
"""Regenerates the rules the sonarLint task bundles: the active rules of the SonarCloud
"Micronaut Profile" (organization micronaut-projects), exported through the public API, no token.

    scripts/update-sonarlint-rules.py [organization] [quality-profile-key]

Projects can export another profile with the sonarLintExportRules task instead.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

ORG = sys.argv[1] if len(sys.argv) > 1 else 'micronaut-projects'
QP = sys.argv[2] if len(sys.argv) > 2 else 'AYFHlJjqlBO-fp5MP_nD'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'micronaut-gradle-plugins', 'src', 'main',
                   'resources', 'io', 'micronaut', 'build', 'sonarlint', 'micronaut-profile-rules.json')

rules, page = [], 1
while True:
    query = urllib.parse.urlencode({'organization': ORG, 'qprofile': QP, 'activation': 'true', 'ps': 500, 'p': page,
                                    'f': 'actives,repo,severity,lang,templateKey,internalKey,name', 's': 'key'})
    with urllib.request.urlopen('https://sonarcloud.io/api/rules/search?' + query) as response:
        body = json.load(response)
    actives = body.get('actives', {})
    for rule in body['rules']:
        activations = actives.get(rule['key'], [])
        active = next((a for a in activations if a.get('qProfile') == QP), activations[0] if activations else {})
        rules.append({
            'key': rule['key'],
            'name': rule.get('name'),
            'severity': active.get('severity', rule.get('severity')),
            'type': rule.get('type'),
            'templateKey': rule.get('templateKey'),
            'internalKey': rule.get('internalKey'),
            'params': {p['key']: p['value'] for p in active.get('params', [])},
        })
    if page * 500 >= body['total']:
        break
    page += 1

with open(OUT, 'w') as f:
    json.dump({'organization': ORG, 'qualityProfile': QP, 'rules': rules}, f, indent=1, sort_keys=True)
    f.write('\n')
print(f'{len(rules)} rules written to {os.path.normpath(OUT)}')
