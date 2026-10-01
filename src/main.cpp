// nfscarbon - ReXGlue Recompiled Project

#include "generated/default/nfscarbon_init.h"

#include "nfscarbon_app.h"

REXCVAR_DEFINE_BOOL(carbon_dump_image, false, "Carbon diagnostics",
                    "Save the loaded executable locally for address analysis");

REX_DEFINE_APP(nfscarbon, NfscarbonApp::Create)
