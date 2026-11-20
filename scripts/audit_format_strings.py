#!/usr/bin/env python3
"""Compares format specifiers across en/pt-BR/es strings and checks lsString/getString arg counts."""
import re, sys, glob
import xml.etree.ElementTree as ET
R='app/src/main/res/'
locs={'en':'values','pt':'values-pt-rBR','es':'values-es'}
SPEC=re.compile(r'%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([sSdfcxXeEgGbB])')
def specs(t):
    t=t.replace('%%','')
    out=[];i=0
    for m in SPEC.finditer(t):
        if m.group(2)=='n': continue
        i+=1
        out.append((int(m.group(1)) if m.group(1) else i, m.group(2)))
    return out
data={}
for k,d in locs.items():
    data[k]={}
    for e in ET.parse(R+d+'/strings.xml').getroot():
        if e.tag=='string': data[k][e.get('name')]=''.join(e.itertext())
        elif e.tag=='plurals':
            for it in e: data[k][e.get('name')+'#'+it.get('quantity')]=''.join(it.itertext())
bad=0
for n,t in data['en'].items():
    se=sorted(specs(t))
    for k in('pt','es'):
        if n in data[k]:
            sk=sorted(specs(data[k][n]))
            if sk!=se: print(f'MISMATCH {k} {n}: en={se} {k}={sk}'); bad+=1
        else: pass
# call sites
call=re.compile(r'(?:lsString|getString)\(\s*(?:R\.string\.(\w+))\s*((?:,[^()]*(?:\([^()]*\))?[^()]*)*)\)')
for f in glob.glob('app/src/main/**/*.kt',recursive=True):
    s=open(f).read()
    for m in call.finditer(s):
        name,args=m.group(1),m.group(2)
        argc=0
        if args.strip():
            depth=0;argc=1
            for c in args.strip().lstrip(','):
                if c in'([{': depth+=1
                elif c in')]}': depth-=1
                elif c==',' and depth==0: argc+=1
        for k in('en','pt','es'):
            if name in data[k]:
                need=len({p for p,_ in specs(data[k][name])})
                if need!=argc:
                    print(f'ARGCOUNT {f}: {name} [{k}] needs {need}, passed {argc}'); bad+=1
print('format audit issues:',bad); sys.exit(1 if bad else 0)
