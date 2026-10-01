#pragma once

#include <cstdint>

namespace rex::runtime { class FunctionDispatcher; }
void CarbonRegisterMissingEntries(rex::runtime::FunctionDispatcher* dispatcher,
                                  uint8_t* base);
