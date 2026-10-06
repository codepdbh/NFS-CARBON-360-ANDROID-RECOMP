# Matches MW functions to Carbon functions by their PowerPC "fingerprint": the multiset of instructions with
# registers removed (mnemonic + immediates + field offsets), plus the sequence of mnemonics. Branch targets and
# absolute address halves (lis/addi of 0x82xx...) are dropped because they move between games.
# Usage: emparejar_funciones.py <mw generated> <carbon generated> <sub_X> [<sub_X> ...]
import collections, glob, os, pickle, re, sys

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'huellas_%s.pkl')
INSTR = re.compile(r'^\s*// ([a-z][a-z0-9.+-]*)\s*(.*)$')


def token(mn, ops):
    if mn in ('b', 'bl', 'bc', 'beq', 'bne', 'blt', 'bgt', 'ble', 'bge', 'bdnz', 'bdz', 'blr', 'bctr', 'bctrl',
              'beqlr', 'bnelr', 'bltlr', 'bgtlr', 'blelr', 'bgelr', 'bso', 'bns'):
        return mn
    partes = [p.strip() for p in ops.split(',') if p.strip()]
    sin_reg = []
    for p in partes:
        if re.fullmatch(r'(r|f|v|cr)\d+', p):
            continue
        m = re.fullmatch(r'(-?\d+)\((r\d+)\)', p)
        if m:
            sin_reg.append('o' + m.group(1))
            continue
        if re.fullmatch(r'0x[0-9a-fA-F]+', p):
            continue  # absolute targets
        sin_reg.append(p)
    if mn == 'lis' and sin_reg and sin_reg[0].lstrip('-').isdigit() and -32256 <= int(sin_reg[0]) <= -31744:
        return 'lis@img'   # high half of an image address (0x8200xxxx-0x83xxxxxx)
    return mn + ' ' + ','.join(sin_reg)


def indexar(gen, nombre):
    ruta = CACHE % nombre
    if os.path.exists(ruta):
        return pickle.load(open(ruta, 'rb'))
    funcs = {}
    for f in glob.glob(os.path.join(gen, '*.cpp')):
        func = None
        toks = []
        for l in open(f, encoding='utf-8', errors='replace'):
            m = re.match(r'(?:DEFINE_REX_FUNC|PPC_FUNC_IMPL|PPC_FUNC)\((?:__imp__)?(sub_[0-9A-F]+)\)', l)
            if m:
                if func and toks:
                    funcs[func] = toks
                func, toks = m.group(1), []
                continue
            m = INSTR.match(l)
            if m and func:
                toks.append(token(m.group(1), m.group(2)))
        if func and toks:
            funcs[func] = toks
    pickle.dump(funcs, open(ruta, 'wb'))
    return funcs


def similitud(a, b):
    ca, cb = collections.Counter(a), collections.Counter(b)
    comun = sum((ca & cb).values())
    return 2.0 * comun / (len(a) + len(b))


def main():
    mw = indexar(sys.argv[1], 'mw')
    cb = indexar(sys.argv[2], 'carbon')
    por_tam = sorted(cb.items(), key=lambda x: len(x[1]))
    for sub in sys.argv[3:]:
        a = mw.get(sub)
        if not a:
            print(sub, 'no esta en MW')
            continue
        n = len(a)
        cands = [(similitud(a, t), s, len(t)) for s, t in por_tam if 0.6 * n <= len(t) <= 1.6 * n + 4]
        cands.sort(reverse=True)
        print('%s (%d instr) -> %s' % (sub, n, ' | '.join('%s %.2f (%d)' % (s, v, l) for v, s, l in cands[:3])))


main()
