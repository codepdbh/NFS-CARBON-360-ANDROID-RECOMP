// NFS Carbon's hooks for the native renderer of NFSMW Android Evolved (nfsmw-android/app/src/nfsmw_nativo_*),
// which is built here with NFSC_RECOMP. Addresses: docs/renderizador-nativo.md.
//
// - The shader constructors tell the renderer which library container each D3D shader object comes from.
// - The four Draw* leave the VS and PS of each draw in the queue the PM4 ring reads (AnotarDibujo before the
//   original, TerminarDibujo after it). Carbon has no separate FlushState: it is inlined in every Draw*.
//
// Most Wanted's fetch patcher hook (IM_LOAD without memcmp) is not ported: Carbon's VS objects keep several
// patched copies and the patcher takes other arguments, so nfsmw_nativo_im_load_sin_memcmp stays off.
#include <cstdint>

#include <rex/hook.h>

#include "nfsmw_nativo_ganchos.h"

namespace {

namespace nativo = nfsmw::nativo;

template <typename Ctx, typename Original>
void GanchoDibujo(nativo::FuncionDibujo funcion, Ctx& ctx, uint8_t* base, Original original) {
  nativo::AnotarDibujo(funcion, base, ctx.r3.u32, ctx.r4.u32, ctx.r5.u32, ctx.r6.u32, ctx.r7.u32);
  original(ctx, base);
  nativo::TerminarDibujo();
}

}  // namespace

// CreatePixelShader / CreateVertexShader (r3 = 2008 container, returns the object in r3).
REX_EXTERN(__imp__sub_826E4218);
REX_HOOK_RAW(sub_826E4218) {
  const auto* entrada = nativo::ganchos_detalle::IdentificarCreacion(base, ctx.r3.u32, false);
  __imp__sub_826E4218(ctx, base);
  nativo::ganchos_detalle::RecordarCreacion(ctx.r3.u32, entrada, false);
}

REX_EXTERN(__imp__sub_826E4680);
REX_HOOK_RAW(sub_826E4680) {
  const auto* entrada = nativo::ganchos_detalle::IdentificarCreacion(base, ctx.r3.u32, true);
  __imp__sub_826E4680(ctx, base);
  nativo::ganchos_detalle::RecordarCreacion(ctx.r3.u32, entrada, true);
}

REX_EXTERN(__imp__sub_826DDA78);
REX_HOOK_RAW(sub_826DDA78) {  // DrawVertices(device, type, start, count)
  GanchoDibujo(nativo::FuncionDibujo::kVertices, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DDA78(c, b); });
}

REX_EXTERN(__imp__sub_826DDE68);
REX_HOOK_RAW(sub_826DDE68) {  // DrawIndexedVertices(device, type, base, start, count)
  GanchoDibujo(nativo::FuncionDibujo::kIndexados, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DDE68(c, b); });
}

REX_EXTERN(__imp__sub_826DD448);
REX_HOOK_RAW(sub_826DD448) {  // DrawVerticesUP(device, type, count, data, stride)
  GanchoDibujo(nativo::FuncionDibujo::kVerticesUP, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DD448(c, b); });
}

REX_EXTERN(__imp__sub_826DD9D8);
REX_HOOK_RAW(sub_826DD9D8) {  // DrawIndexedVerticesUP
  GanchoDibujo(nativo::FuncionDibujo::kIndexadosUP, ctx, base,
               [](auto& c, uint8_t* b) { __imp__sub_826DD9D8(c, b); });
}

// Carbon's other six draw functions, reached through function pointers (each with its own inlined FlushState;
// docs/renderizador-nativo.md). Their arguments are not mapped yet: kCualquiera records only carry the shaders.
REX_EXTERN(__imp__sub_826DE280);
REX_HOOK_RAW(sub_826DE280) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DE280(c, b); });
}

REX_EXTERN(__imp__sub_826DE700);
REX_HOOK_RAW(sub_826DE700) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DE700(c, b); });
}

REX_EXTERN(__imp__sub_826F6A88);
REX_HOOK_RAW(sub_826F6A88) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826F6A88(c, b); });
}

REX_EXTERN(__imp__sub_82706068);
REX_HOOK_RAW(sub_82706068) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_82706068(c, b); });
}

REX_EXTERN(__imp__sub_826DCB30);
REX_HOOK_RAW(sub_826DCB30) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DCB30(c, b); });
}

REX_EXTERN(__imp__sub_826DCD68);
REX_HOOK_RAW(sub_826DCD68) {
  GanchoDibujo(nativo::FuncionDibujo::kCualquiera, ctx, base, [](auto& c, uint8_t* b) { __imp__sub_826DCD68(c, b); });
}
