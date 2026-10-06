# Functions of a generated tree whose PowerPC comments contain all the given patterns (regexes).
# Usage: buscar_patrones.py <generated folder> <regex> [<regex> ...]
import glob, os, re, sys

gen = sys.argv[1]
patrones = [re.compile(p) for p in sys.argv[2:]]
for f in sorted(glob.glob(os.path.join(gen, '*.cpp'))):
    func = None
    vistos = set()
    def cerrar():
        if func and len(vistos) == len(patrones):
            print(os.path.basename(f), func)
    for l in open(f, encoding='utf-8', errors='replace'):
        m = re.match(r'(?:DEFINE_REX_FUNC|PPC_FUNC_IMPL|PPC_FUNC)\((?:__imp__)?(sub_[0-9A-F]+)\)', l)
        if m:
            cerrar()
            func = m.group(1)
            vistos = set()
            continue
        if l.lstrip().startswith('//'):
            for i, p in enumerate(patrones):
                if p.search(l):
                    vistos.add(i)
    cerrar()
