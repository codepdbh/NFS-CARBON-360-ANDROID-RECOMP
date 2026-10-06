# Prints the PowerPC disassembly (comments) of a generated function: funcion.py <file> <line inside> | <sub_X>
import glob, re, sys

import os
GEN = os.environ.get("GEN", "C:/Users/Sistemas/Documents/nfsmw android evolved/nfsmw-android/app/generated/default")


def mostrar(lineas, inicio):
    for l in lineas[inicio:]:
        if l.startswith('DEFINE_REX_FUNC(') and l is not lineas[inicio]:
            break
        m = re.match(r'\s*// (.*)', l)
        if m and not m.group(1).startswith('PPC'):
            print('   ', m.group(1))
        elif l.startswith('loc_') or l.startswith('DEFINE_REX_FUNC'):
            print(l)
        elif '__imp__' in l or re.search(r'\bsub_[0-9A-F]{8}\(ctx', l):
            print('        ->', l.strip())


if sys.argv[1].startswith('sub_'):
    for f in glob.glob(GEN + '/*_recomp.*.cpp'):
        L = open(f, encoding='utf-8', errors='replace').read().splitlines()
        for i, l in enumerate(L):
            if l.startswith('DEFINE_REX_FUNC(%s)' % sys.argv[1]):
                mostrar(L, i)
                sys.exit(0)
else:
    L = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
    n = int(sys.argv[2])
    i = max(k for k in range(n) if L[k].startswith('DEFINE_REX_FUNC('))
    mostrar(L, i)
