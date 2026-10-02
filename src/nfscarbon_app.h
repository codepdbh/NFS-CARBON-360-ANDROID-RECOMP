// nfscarbon - ReXGlue Recompiled Project
//
// Customize your app by overriding virtual hooks from rex::ReXApp.

#pragma once

#include <rex/rex_app.h>
#include <rex/logging.h>
#include <fstream>
#include "carbon_missing_entries.h"

REXCVAR_DECLARE(bool, carbon_dump_image);
#ifdef CARBON_RECOVERED_THUNKS
void CarbonRegisterRecoveredEntries(rex::runtime::FunctionDispatcher*, uint8_t*);
#endif

class NfscarbonApp : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<NfscarbonApp>(new NfscarbonApp(ctx, "nfscarbon",
        PPCImageConfig));
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    config.gpu_plugin = "xenos";
  }

#if REX_PLATFORM_ANDROID
  void OnConfigurePaths(rex::PathConfig& paths) override {
    paths.config_path = paths.user_data_root / "nfscarbon.toml";
  }
#endif

  void OnPreLaunchModule() override {
    CarbonRegisterMissingEntries(runtime()->function_dispatcher(),
                                  runtime()->virtual_membase());
#ifdef CARBON_RECOVERED_THUNKS
    CarbonRegisterRecoveredEntries(runtime()->function_dispatcher(),
                                  runtime()->virtual_membase());
#endif
    if (!REXCVAR_GET(carbon_dump_image)) return;
    // Diagnostic image for this verified XEX only; never a gameplay patch.
    // It stays in ignored local output, alongside the runtime log.
    auto path = runtime()->cache_root() / "carbon-82000000.bin";
    std::ofstream image(path, std::ios::binary);
    image.write(reinterpret_cast<const char*>(runtime()->virtual_membase() +
                PPCImageConfig.image_base), PPCImageConfig.image_size);
    if (image) REXLOG_INFO("[carbon] Loaded image saved to {}", path.string());
    else REXLOG_WARN("[carbon] Could not save diagnostic image {}", path.string());
  }

  // Override virtual hooks for customization:
  // void OnPostInitLogging() override {}
  // void OnPreSetup(rex::RuntimeConfig& config) override {}
  // void OnLoadXexImage(std::string& xex_image) override {}
  // void OnPostLoadXexImage() override {}
  // void OnPostSetup() override {}
  // void OnCreateDialogs(rex::ui::ImGuiDrawer* drawer) override {}
  // std::unique_ptr<rex::ui::ImGuiDialog> CreateAchievementsOverlay() override;
  // std::unique_ptr<rex::ui::AchievementNotificationDialog>
  // CreateAchievementNotificationDialog() override;
  // void OnShutdown() override {}
  // void OnConfigurePaths(rex::PathConfig& paths) override {}
};
