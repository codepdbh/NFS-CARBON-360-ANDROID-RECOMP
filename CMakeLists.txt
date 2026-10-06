# nfscarbon - ReXGlue Recompiled Project
#
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(nfscarbon LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Use Xenos with the host's graphics API.
if(ANDROID)
    set(REXGLUE_USE_D3D12 OFF CACHE BOOL "Android uses Vulkan" FORCE)
    set(REXGLUE_USE_VULKAN ON CACHE BOOL "Android uses Vulkan" FORCE)
else()
    set(REXGLUE_USE_D3D12 ON CACHE BOOL "PC test backend" FORCE)
    set(REXGLUE_USE_VULKAN OFF CACHE BOOL "PC test backend" FORCE)
endif()
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "No profiler dependency" FORCE)
set(REXGLUE_ENABLE_FIDELITYFX OFF CACHE BOOL "Initial renderer test" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "Build the game target only" FORCE)
set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "Shared local SDK")
set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Keep the initial build small")

include(generated/rexglue.cmake)

# Carbon's Android surface lifecycle fix stays local to this project.
if(ANDROID AND TARGET rexui)
    get_target_property(carbon_ui_sources rexui SOURCES)
    set(carbon_ui_replaced_sources "")
    foreach(ui_source IN LISTS carbon_ui_sources)
        if(ui_source MATCHES "(^|/)window_sdl\\.cpp$")
            continue()
        endif()
        if(IS_ABSOLUTE "${ui_source}" OR ui_source MATCHES "^\\$<")
            list(APPEND carbon_ui_replaced_sources "${ui_source}")
        else()
            list(APPEND carbon_ui_replaced_sources "${REXSDK_DIR}/src/ui/${ui_source}")
        endif()
    endforeach()
    list(APPEND carbon_ui_replaced_sources "${CMAKE_CURRENT_SOURCE_DIR}/src/ui/window_sdl.cpp")
    set_property(TARGET rexui PROPERTY SOURCES "${carbon_ui_replaced_sources}")
endif()

# Use Carbon's corrected gap analysis without changing the shared MW SDK.
if(TARGET rexcodegen)
    get_target_property(carbon_codegen_sources rexcodegen SOURCES)
    set(carbon_codegen_replaced_sources "")
    foreach(codegen_source IN LISTS carbon_codegen_sources)
        if(codegen_source MATCHES "(^|/)phase_gapfill\\.cpp$")
            continue()
        endif()
        if(IS_ABSOLUTE "${codegen_source}" OR codegen_source MATCHES "^\\$<")
            list(APPEND carbon_codegen_replaced_sources "${codegen_source}")
        else()
            list(APPEND carbon_codegen_replaced_sources "${REXSDK_DIR}/src/codegen/${codegen_source}")
        endif()
    endforeach()
    list(APPEND carbon_codegen_replaced_sources "${CMAKE_CURRENT_SOURCE_DIR}/src/codegen/phase_gapfill.cpp")
    set_property(TARGET rexcodegen PROPERTY SOURCES "${carbon_codegen_replaced_sources}")
endif()

# The local SDK normally writes binaries back into its source checkout. Keep
# this experiment's binaries here, including its code generator and plugins.
function(carbon_stage_sdk_outputs directory)
    get_property(local_targets DIRECTORY "${directory}" PROPERTY BUILDSYSTEM_TARGETS)
    foreach(local_target IN LISTS local_targets)
        get_target_property(local_type ${local_target} TYPE)
        if(NOT local_type STREQUAL "INTERFACE_LIBRARY" AND NOT local_type STREQUAL "UTILITY")
            set_target_properties(${local_target} PROPERTIES
                RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin"
                LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin"
                ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/sdk-bin")
        endif()
    endforeach()
    get_property(child_directories DIRECTORY "${directory}" PROPERTY SUBDIRECTORIES)
    foreach(child_directory IN LISTS child_directories)
        carbon_stage_sdk_outputs("${child_directory}")
    endforeach()
endfunction()
if(REXSDK_DIR)
    carbon_stage_sdk_outputs("${REXSDK_DIR}")
endif()

# Sources
set(NFSCARBON_SOURCES
    src/main.cpp
    src/carbon_missing_entries.cpp
)

if(ANDROID)
    add_library(nfscarbon SHARED ${NFSCARBON_SOURCES})
    set_target_properties(nfscarbon PROPERTIES OUTPUT_NAME main)
elseif(WIN32)
    add_executable(nfscarbon WIN32 ${NFSCARBON_SOURCES})
else()
    add_executable(nfscarbon ${NFSCARBON_SOURCES})
endif()

rexglue_setup_target(nfscarbon GPU_PLUGINS xenos)

# Native renderer of NFSMW Android Evolved (docs/renderizador-nativo.md), built from its sources with NFS
# Carbon's addresses (NFSC_RECOMP) and Carbon's hooks (src/carbon_nativo_ganchos.cpp). Off unless
# nfsmw_renderizador=nativo.
# Android only: it draws with Vulkan, and the PC test build uses Xenos on Direct3D 12.
if(ANDROID)
get_filename_component(NFSC_NFSMW_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../nfsmw-android/app/src" ABSOLUTE)
target_sources(nfscarbon PRIVATE
    src/carbon_nativo_ganchos.cpp
    src/carbon_nativo_enlaces.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_sistema.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_destinos.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_dibujos.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_shaders.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_ganchos.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_texturas_pool.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_nativo_captura.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_shader_library.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_espera_anillo.cpp
    ${NFSC_NFSMW_SRC}/nfsmw_ajustes_graficos.cpp)
target_include_directories(nfscarbon PRIVATE
    "${NFSC_NFSMW_SRC}" "${REXSDK_DIR}/src/ui/shaders" "${REXSDK_DIR}/thirdparty/xxHash")
target_compile_definitions(nfscarbon PRIVATE NFSC_RECOMP=1)
endif()
if(ANDROID)
    # The shared MW SDK selects its own app name in its Android entry point.
    # Keep that SDK untouched and select Carbon in this project's bootstrap.
    get_target_property(carbon_app_sources nfscarbon SOURCES)
    list(FILTER carbon_app_sources EXCLUDE REGEX "(^|/)windowed_app_main_sdl\\.cpp$")
    set_property(TARGET nfscarbon PROPERTY SOURCES "${carbon_app_sources}")
    target_sources(nfscarbon PRIVATE android/app/src/main/cpp/carbon_entry.cpp)
endif()
if(ANDROID AND TARGET nfscarbon_recomp)
    # ReXApp registers the complete PPCImageConfig.func_mappings array from
    # nfscarbon_init.cpp. The optional 72k-call RegisterFunctions helper has
    # no callers in this app and takes minutes to compile for AArch64.
    get_target_property(carbon_recomp_sources nfscarbon_recomp SOURCES)
    list(FILTER carbon_recomp_sources EXCLUDE REGEX "(^|/)nfscarbon_register\\.cpp$")
    set_property(TARGET nfscarbon_recomp PROPERTY SOURCES "${carbon_recomp_sources}")
endif()
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/generated/carbon_recovered_thunks.cpp")
    target_sources(nfscarbon PRIVATE generated/carbon_recovered_thunks.cpp)
    target_compile_definitions(nfscarbon PRIVATE CARBON_RECOVERED_THUNKS=1)
endif()
foreach(ui_dependency imgui renderdoc)
    if(TARGET ${ui_dependency})
        target_link_libraries(nfscarbon PRIVATE ${ui_dependency})
    endif()
endforeach()

if(WIN32)
    add_executable(carbon_entry_checks EXCLUDE_FROM_ALL tests/story_entry_checks.cpp)
    target_include_directories(carbon_entry_checks PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
    target_link_libraries(carbon_entry_checks PRIVATE rex::runtime)
    rexglue_apply_target_settings(carbon_entry_checks)
endif()
