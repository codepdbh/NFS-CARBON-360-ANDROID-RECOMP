# Call graph of a generated tree: callees ("bl 0x...") and callers of each function.
# Usage: grafo.py <generated folder> <name> callees|callers <sub_X> [...]
import glob, os, pickle, re, sys

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'grafo_%s.pkl')


def construir(gen, nombre):
    ruta = CACHE % nombre
    if os.path.exists(ruta):
        return pickle.load(open(ruta, 'rb'))
    llamadas = {}
    for f in glob.glob(os.path.join(gen, '*.cpp')):
        func = None
        for l in open(f, encoding='utf-8', errors='replace'):
            m = re.match(r'(?:DEFINE_REX_FUNC|PPC_FUNC_IMPL|PPC_FUNC)\((?:__imp__)?(sub_[0-9A-F]+)\)', l)
            if m:
                func = m.group(1)
                llamadas.setdefault(func, [])
                continue
            m = re.match(r'\s*// bl 0x([0-9a-f]{8})', l)
            if m and func:
                llamadas[func].append('sub_' + m.group(1).upper())
    pickle.dump(llamadas, open(ruta, 'wb'))
    return llamadas


if __name__ == '__main__':
    g = construir(sys.argv[1], sys.argv[2])
    modo = sys.argv[3]
    for s in sys.argv[4:]:
        if modo == 'callees':
            print(s, '->', ' '.join(g.get(s, [])))
        else:
            print(s, '<-', ' '.join(sorted({f for f, c in g.items() if s in c})))
