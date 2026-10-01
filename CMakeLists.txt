# nfscarbon - ReXGlue Recompiled Project
#
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(nfscarbon LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Initial PC bring-up uses the generic Xenos backend. Game-specific hooks from
# Most Wanted are intentionally absent from this independent project.
set(REXGLUE_USE_D3D12 ON CACHE BOOL "PC test backend" FORCE)
set(REXGLUE_USE_VULKAN OFF CACHE BOOL "PC test backend" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "No profiler dependency" FORCE)
set(REXGLUE_ENABLE_FIDELITYFX OFF CACHE BOOL "Initial renderer test" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "Build the game target only" FORCE)
set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "Shared local SDK")
set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Keep the initial build small")

include(generated/rexglue.cmake)

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

if(WIN32)
    add_executable(nfscarbon WIN32 ${NFSCARBON_SOURCES})
else()
    add_executable(nfscarbon ${NFSCARBON_SOURCES})
endif()

rexglue_setup_target(nfscarbon GPU_PLUGINS xenos)
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
