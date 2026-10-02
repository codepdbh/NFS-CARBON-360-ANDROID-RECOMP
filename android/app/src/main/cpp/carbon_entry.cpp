// Adapted from ReXGlue's SDL entry point.
// Copyright (c) 2026 Tom Clay <tomc@tctechstuff.com>
// SPDX-License-Identifier: BSD-3-Clause

#include <algorithm>
#include <cstdlib>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>
#include <SDL3/SDL_main.h>

int main(int argc, char* argv[]) {
  auto remaining = rex::cvar::Init(argc, argv);
  rex::cvar::ApplyEnvironment();
  rex::InitLoggingEarly();

  rex::ui::SDLWindowedAppContext context;
  if (!context.Initialize()) return EXIT_FAILURE;

  auto creator = rex::ui::WindowedApp::GetCreator("nfscarbon");
  if (!creator) {
    REXLOG_ERROR("[carbon] Android app creator was not registered");
    return EXIT_FAILURE;
  }
  auto app = creator(context);
  const auto& options = app->GetPositionalOptions();
  std::map<std::string, std::string> parsed;
  for (size_t i = 0; i < std::min(remaining.size(), options.size()); ++i) {
    parsed[options[i]] = remaining[i];
  }
  app->SetParsedArguments(std::move(parsed));
  int result = app->OnInitialize() ? context.RunMainMessageLoop() : EXIT_FAILURE;
  app->InvokeOnDestroy();
  return result;
}
