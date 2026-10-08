cmake_minimum_required(VERSION 3.25)
project(nfscarbon_android LANGUAGES C CXX)
function(carbon_write_if_changed path content)
    if(EXISTS "${path}")
        file(READ "${path}" previous)
        if(previous STREQUAL content)
            return()
        endif()
    endif()
    file(WRITE "${path}" "${content}")
endfunction()
get_filename_component(CARBON_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../.." ABSOLUTE)
if(NOT REXSDK_DIR)
    get_filename_component(REXSDK_DIR "${CARBON_ROOT}/../nfsmw-android/sdk" ABSOLUTE)
endif()
set(REXSDK_DIR "${REXSDK_DIR}" CACHE PATH "ReXGlue SDK sources" FORCE)
set(SDL_SHARED ON CACHE BOOL "SDLActivity loads libSDL3.so" FORCE)
set(SDL_STATIC OFF CACHE BOOL "Use shared SDL" FORCE)
add_library(nfscarbon_diagnostics SHARED android_diagnostics.cpp)
target_include_directories(nfscarbon_diagnostics PRIVATE "${REXSDK_DIR}/thirdparty/vulkan-headers/include"
    "${REXSDK_DIR}/include")
target_link_options(nfscarbon_diagnostics PRIVATE "-Wl,-z,max-page-size=16384")
add_subdirectory("${CARBON_ROOT}" "carbon-app")
target_link_libraries(nfscarbon_diagnostics PRIVATE rex_android_gpu_driver dl)
# Package the custom-driver loader and its namespace hooks alongside SDL and the game.
add_dependencies(nfscarbon adrenotools main_hook hook_impl file_redirect_hook gsl_alloc_hook)
target_sources(nfscarbon PRIVATE android_touch.cpp carbon_performance.cpp carbon_graphics.cpp carbon_commands.cpp)
target_link_libraries(nfscarbon PRIVATE rexgpu-xenos)

# SDL's Android event wait wakes itself when it inserts its polling sentinel.
# Do not send a wake for a sentinel-only batch; real input/lifecycle wakes remain.
# Generate an explicitly altered SDL source, retaining its complete license.
file(READ "${REXSDK_DIR}/thirdparty/sdl3/src/events/SDL_events.c" carbon_sdl_source)
set(carbon_sdl_before "                used += SDL_AddEvent(&events[i]);")
string(FIND "${carbon_sdl_source}" "${carbon_sdl_before}" carbon_sdl_match)
if(carbon_sdl_match EQUAL -1)
    message(FATAL_ERROR "SDL event insertion changed; review Carbon event wait fix")
endif()
string(REPLACE "int i, used, sentinels_expected = 0;"
    "int i, used, sentinels_expected = 0;\n    int wakeup_events = 0;" carbon_sdl_source "${carbon_sdl_source}")
string(REPLACE "${carbon_sdl_before}"
    "                int added = SDL_AddEvent(&events[i]);\n                used += added;\n                if (added > 0 && events[i].type != SDL_EVENT_POLL_SENTINEL) ++wakeup_events;"
    carbon_sdl_source "${carbon_sdl_source}")
string(REPLACE "if (used > 0 && action == SDL_ADDEVENT)"
    "if (wakeup_events > 0 && action == SDL_ADDEVENT)" carbon_sdl_source "${carbon_sdl_source}")
carbon_write_if_changed("${CMAKE_CURRENT_BINARY_DIR}/carbon_SDL_events.c"
    "/* Altered for Carbon: avoid sentinel-only self-wakes. */\n${carbon_sdl_source}")
get_target_property(carbon_sdl_sources SDL3-shared SOURCES)
list(FILTER carbon_sdl_sources EXCLUDE REGEX "(^|/)SDL_events\\.c$")
list(APPEND carbon_sdl_sources "${CMAKE_CURRENT_BINARY_DIR}/carbon_SDL_events.c")
set_property(TARGET SDL3-shared PROPERTY SOURCES "${carbon_sdl_sources}")
target_include_directories(SDL3-shared PRIVATE "${REXSDK_DIR}/thirdparty/sdl3/src/events")

# QueryProtect is hot in the write-watch fault handler on Android. Read the
# current maps using fixed buffers, rather than allocating/parsing every line.
file(READ "${REXSDK_DIR}/src/core/memory_posix.cpp" carbon_memory_source)
set(carbon_memory_start "static bool FindEntryForAddress(void* address, LinuxMapEntry& out_entry) {")
set(carbon_memory_end "// Check if [base, base+length) is fully covered by existing mappings (no gaps)")
string(FIND "${carbon_memory_source}" "${carbon_memory_start}" carbon_memory_begin)
string(FIND "${carbon_memory_source}" "${carbon_memory_end}" carbon_memory_finish)
if(carbon_memory_begin LESS 0 OR carbon_memory_finish LESS carbon_memory_begin)
    message(FATAL_ERROR "SDK memory query changed; review Carbon map reader")
endif()
string(SUBSTRING "${carbon_memory_source}" 0 ${carbon_memory_begin} carbon_memory_prefix)
string(SUBSTRING "${carbon_memory_source}" ${carbon_memory_finish} -1 carbon_memory_suffix)
carbon_write_if_changed("${CMAKE_CURRENT_BINARY_DIR}/carbon_memory_posix.cpp"
    "#include \"${CMAKE_CURRENT_LIST_DIR}/carbon_proc_maps.h\"\n${carbon_memory_prefix}${carbon_memory_start}\n  carbon::MapEntry entry;\n  if (!carbon::FindProcMapEntry(address, entry)) return false;\n  out_entry.start = entry.start;\n  out_entry.end = entry.end;\n  std::memcpy(out_entry.perms, entry.perms, sizeof(entry.perms));\n  return true;\n}\n\n${carbon_memory_suffix}")
get_target_property(carbon_core_sources rexcore SOURCES)
set(carbon_core_local_sources "")
foreach(core_source IN LISTS carbon_core_sources)
    if(core_source MATCHES "(^|/)memory_posix\\.cpp$")
        continue()
    endif()
    if(IS_ABSOLUTE "${core_source}" OR core_source MATCHES "^\\$<")
        list(APPEND carbon_core_local_sources "${core_source}")
    else()
        list(APPEND carbon_core_local_sources "${REXSDK_DIR}/src/core/${core_source}")
    endif()
endforeach()
list(APPEND carbon_core_local_sources "${CMAKE_CURRENT_BINARY_DIR}/carbon_memory_posix.cpp")
set_property(TARGET rexcore PROPERTY SOURCES "${carbon_core_local_sources}")
target_include_directories(rexcore PRIVATE "${REXSDK_DIR}/src/core")

# The SDK's OpenFile looks up the last component directly, bypassing file aliases.
# Honor read-only movie aliases without changing the shared SDK checkout.
file(READ "${REXSDK_DIR}/src/filesystem/virtual_file_system.cpp" carbon_vfs_source)
set(carbon_vfs_original "    entry = parent_entry->GetChild(file_name);")
set(carbon_vfs_alias "    entry = parent_entry->GetChild(file_name);\n    if (!entry && !root_entry && creation_disposition == FileDisposition::kOpen &&\n        !(desired_access & (FileAccess::kFileWriteData | FileAccess::kFileAppendData))) {\n      entry = ResolvePath(path);\n      if (entry) parent_entry = entry->parent();\n    }")
string(FIND "${carbon_vfs_source}" "${carbon_vfs_original}" carbon_vfs_match)
if(carbon_vfs_match EQUAL -1)
    message(FATAL_ERROR "SDK OpenFile changed; review Carbon movie alias integration")
endif()
string(REPLACE "${carbon_vfs_original}" "${carbon_vfs_alias}" carbon_vfs_source "${carbon_vfs_source}")
carbon_write_if_changed("${CMAKE_CURRENT_BINARY_DIR}/carbon_virtual_file_system.cpp" "${carbon_vfs_source}")
get_target_property(carbon_vfs_sources rexfilesystem SOURCES)
set(carbon_vfs_local_sources "")
foreach(vfs_source IN LISTS carbon_vfs_sources)
    if(vfs_source MATCHES "(^|/)virtual_file_system\\.cpp$")
        continue()
    endif()
    if(IS_ABSOLUTE "${vfs_source}" OR vfs_source MATCHES "^\\$<")
        list(APPEND carbon_vfs_local_sources "${vfs_source}")
    else()
        list(APPEND carbon_vfs_local_sources "${REXSDK_DIR}/src/filesystem/${vfs_source}")
    endif()
endforeach()
list(APPEND carbon_vfs_local_sources "${CMAKE_CURRENT_BINARY_DIR}/carbon_virtual_file_system.cpp")
set_property(TARGET rexfilesystem PROPERTY SOURCES "${carbon_vfs_local_sources}")

# Keep the SDK checkout unchanged, and measure guest swaps in Release builds.
# Enabling counters for the whole runtime would add atomics to every PPC call.
file(READ "${REXSDK_DIR}/src/graphics/command_processor.cpp" carbon_cp_source)
foreach(carbon_cp_store "read_ptr_index_" "ahora")
    set(carbon_cp_after "                                       ${carbon_cp_store});")
    string(FIND "${carbon_cp_source}" "${carbon_cp_after}" carbon_cp_match)
    if(carbon_cp_match EQUAL -1)
        message(FATAL_ERROR "GPU pointer publication changed; review Carbon progress notification")
    endif()
    string(REPLACE "${carbon_cp_after}" "${carbon_cp_after}\n        CarbonNotifyGpu();"
        carbon_cp_source "${carbon_cp_source}")
endforeach()
carbon_write_if_changed("${CMAKE_CURRENT_BINARY_DIR}/carbon_command_processor.cpp" "${carbon_cp_source}")
target_include_directories(rexgpu-xenos PRIVATE "${REXSDK_DIR}/src/graphics")
configure_file(carbon_frame_counters.cpp.in "${CMAKE_CURRENT_BINARY_DIR}/carbon_frame_counters.cpp" @ONLY)
get_target_property(carbon_gpu_sources rexgpu-xenos SOURCES)
set(carbon_gpu_measured_sources "")
foreach(gpu_source IN LISTS carbon_gpu_sources)
    if(gpu_source STREQUAL "command_processor.cpp" OR
       gpu_source STREQUAL "${REXSDK_DIR}/src/graphics/command_processor.cpp")
        continue()
    endif()
    if(IS_ABSOLUTE "${gpu_source}" OR gpu_source MATCHES "^\\$<")
        list(APPEND carbon_gpu_measured_sources "${gpu_source}")
    else()
        list(APPEND carbon_gpu_measured_sources "${REXSDK_DIR}/src/graphics/${gpu_source}")
    endif()
endforeach()
list(APPEND carbon_gpu_measured_sources "${CMAKE_CURRENT_BINARY_DIR}/carbon_frame_counters.cpp")
set_property(TARGET rexgpu-xenos PROPERTY SOURCES "${carbon_gpu_measured_sources}")
set_source_files_properties("${CMAKE_CURRENT_BINARY_DIR}/carbon_frame_counters.cpp"
    PROPERTIES SKIP_PRECOMPILE_HEADERS ON)
target_link_options(nfscarbon PRIVATE "-Wl,-z,max-page-size=16384")
foreach(carbon_target nfscarbon rexruntime)
    # Keep each FFmpeg instance's assembly DSP bindings and FFT tables together.
    target_link_options(${carbon_target} PRIVATE
        "LINKER:--exclude-libs,liblibavcodec.a:liblibavutil.a")
endforeach()
if(TARGET SDL3-shared)
    set_target_properties(SDL3-shared PROPERTIES OUTPUT_NAME SDL3 DEBUG_POSTFIX ""
        RELEASE_POSTFIX "" RELWITHDEBINFO_POSTFIX "" MINSIZEREL_POSTFIX "")
endif()
# Limit parallel compilation on the development machine's 16 GB RAM.
set_property(GLOBAL PROPERTY JOB_POOLS carbon_compile=4 carbon_link=1)
set(CMAKE_JOB_POOL_COMPILE carbon_compile CACHE STRING "Compile concurrency" FORCE)
foreach(carbon_target nfscarbon nfscarbon_recomp rexruntime rexgpu-xenos)
    if(TARGET ${carbon_target})
        set_property(TARGET ${carbon_target} PROPERTY JOB_POOL_COMPILE carbon_compile)
        set_property(TARGET ${carbon_target} PROPERTY JOB_POOL_LINK carbon_link)
    endif()
endforeach()

# The same compiler strategy as Most Wanted: direct calls to verified unhooked
# originals plus ThinLTO, keeping dynamic dispatch and all hooks intact.
option(CARBON_ANDROID_LTO "Direct guest calls and ThinLTO in Android Release" ON)
if(CARBON_ANDROID_LTO AND CMAKE_BUILD_TYPE STREQUAL "Release")
    find_package(Python3 REQUIRED COMPONENTS Interpreter)
    file(GLOB_RECURSE carbon_hook_inputs CONFIGURE_DEPENDS
        "${CARBON_ROOT}/src/*.cpp" "${CARBON_ROOT}/src/*.h"
        "${CMAKE_CURRENT_LIST_DIR}/*.cpp" "${CMAKE_CURRENT_LIST_DIR}/*.h"
        "${CMAKE_CURRENT_LIST_DIR}/*.in"
        "${REXSDK_DIR}/src/*.cpp" "${REXSDK_DIR}/src/*.h"
        "${REXSDK_DIR}/include/*.h"
        "${CARBON_ROOT}/generated/default/nfscarbon_recomp.*.cpp")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
        ${carbon_hook_inputs} "${CARBON_ROOT}/tools/android_direct_calls.py")
    execute_process(COMMAND "${Python3_EXECUTABLE}" "${CARBON_ROOT}/tools/android_direct_calls.py"
        --root "${CARBON_ROOT}" --sdk "${REXSDK_DIR}"
        --output "${CMAKE_CURRENT_BINARY_DIR}/carbon-direct"
        COMMAND_ERROR_IS_FATAL ANY)
    get_target_property(carbon_direct_sources nfscarbon_recomp SOURCES)
    set(carbon_direct_local_sources "")
    foreach(recomp_source IN LISTS carbon_direct_sources)
        get_filename_component(recomp_name "${recomp_source}" NAME)
        if(recomp_name MATCHES "^nfscarbon_recomp\\.[0-9]+\\.cpp$")
            list(APPEND carbon_direct_local_sources "${CMAKE_CURRENT_BINARY_DIR}/carbon-direct/${recomp_name}")
        else()
            list(APPEND carbon_direct_local_sources "${recomp_source}")
        endif()
    endforeach()
    set_property(TARGET nfscarbon_recomp PROPERTY SOURCES "${carbon_direct_local_sources}")
    foreach(carbon_target nfscarbon nfscarbon_recomp)
        target_compile_options(${carbon_target} PRIVATE -flto=thin)
    endforeach()
    target_link_options(nfscarbon PRIVATE -flto=thin -Wl,--thinlto-jobs=4
        "-Wl,--thinlto-cache-dir=${CMAKE_CURRENT_BINARY_DIR}/thinlto-cache" -Wl,--icf=safe)
endif()
