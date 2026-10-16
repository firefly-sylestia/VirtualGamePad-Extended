cmake_minimum_required(VERSION 3.19)

set(APP_VERSION 0.5.1)
add_definitions(-DAPP_VERSION="${APP_VERSION}")

project("VirtualGamePad Extended" VERSION ${APP_VERSION} LANGUAGES CXX C)

# Build mode option: portable vs installable
# Portable: stores data alongside executable (default)
# Installable: stores data in standard OS locations (~/.config, %APPDATA%, etc.)
option(PORTABLE_BUILD "Create a portable build that stores data alongside executable" ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Platform validation
if (NOT WIN32 AND NOT LINUX)
    message(FATAL_ERROR "This project only supports Windows and Linux")
endif()

# Add clang-format target for code formatting
find_program(CLANG_FORMAT "clang-format")
if(CLANG_FORMAT)
    # Get all source files
    file(GLOB_RECURSE ALL_SOURCE_FILES 
        "${CMAKE_SOURCE_DIR}/src/*.cpp"
        "${CMAKE_SOURCE_DIR}/src/*.hpp"
        "${CMAKE_SOURCE_DIR}/src/*.h"
    )
    
    add_custom_target(format
        COMMAND ${CLANG_FORMAT} -style=file -i ${ALL_SOURCE_FILES}
        COMMENT "Formatting source code with clang-format"
        VERBATIM
    )
    
    message(STATUS "clang-format found: ${CLANG_FORMAT}")
    message(STATUS "Format target added. Use 'cmake --build build --target format' to format the code.")
else()
    message(STATUS "clang-format not found. Format target not created.")
endif()

set(CMAKE_AUTOUIC ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(Qt6 REQUIRED COMPONENTS Core Widgets Network)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(OpenSSFHardening)

# Platform-specific setup
if(WIN32)
    # Include CppWinRT setup for Windows
    include(CppWinRT)
elseif(LINUX)
    # Include Linux input dependencies
    include(LinuxInput)
endif()

# --- ViGEmBus: the driver the virtual gamepad is built on -------------------
#
# The Windows backend creates its controller through the ViGEmBus kernel driver.
# Without that driver there is no virtual pad at all: the app falls back to the
# Windows input-injection API, which writes into the system input queue, while
# games read XInput, DirectInput or RawInput and therefore see no controller.
# Carrying the installer next to the application is what lets the app offer to
# install the driver in one click instead of sending the user off to find it.
#
# The download is checksum-pinned and deliberately non-fatal: an offline build
# still succeeds and simply does not ship an installer.
if(WIN32)
    option(VGP_BUNDLE_VIGEMBUS "Download and ship the ViGEmBus driver installer" ON)

    set(VGP_VIGEMBUS_VERSION "1.22.0" CACHE STRING "ViGEmBus release to ship")
    set(VGP_VIGEMBUS_ASSET "ViGEmBus_${VGP_VIGEMBUS_VERSION}_x64_x86_arm64.exe")
    set(VGP_VIGEMBUS_URL
        "https://github.com/nefarius/ViGEmBus/releases/download/v${VGP_VIGEMBUS_VERSION}/${VGP_VIGEMBUS_ASSET}"
    )
    # SHA-512 of the official release asset, so a mirror, a proxy or a bad
    # connection cannot put something else in the distribution. Bump this
    # together with VGP_VIGEMBUS_VERSION.
    set(VGP_VIGEMBUS_SHA512
        "960b2cf7d3e301fc8a260ed49a136ffe8c1e30406a4eb64b7ceeaf7f56f33bbb5153825670258a2e254d3dbdb09c0f0f4abffcbb70395416f213a0e2b8061366"
    )

    # A cache entry so an offline build can point this at a local copy and still
    # ship the driver.
    set(VGP_VIGEMBUS_INSTALLER "${CMAKE_BINARY_DIR}/vigembus/${VGP_VIGEMBUS_ASSET}"
        CACHE FILEPATH "ViGEmBus installer to bundle next to the application"
    )

    if(VGP_BUNDLE_VIGEMBUS AND NOT EXISTS "${VGP_VIGEMBUS_INSTALLER}")
        # EXPECTED_HASH is deliberately not used here: a hash mismatch makes
        # file(DOWNLOAD) raise a fatal error, which would abort the whole
        # configure step and leave the unverified file behind for the packaging
        # steps to pick up. The download is verified explicitly below instead, so
        # every failure mode stays a warning and nothing bad is ever shipped.
        file(DOWNLOAD "${VGP_VIGEMBUS_URL}" "${VGP_VIGEMBUS_INSTALLER}"
            TLS_VERIFY ON
            STATUS vgp_vigembus_download
        )
        list(GET vgp_vigembus_download 0 vgp_vigembus_code)
        if(NOT vgp_vigembus_code EQUAL 0)
            list(GET vgp_vigembus_download 1 vgp_vigembus_message)
            file(REMOVE "${VGP_VIGEMBUS_INSTALLER}")
            message(WARNING
                "Could not download the ViGEmBus installer: ${vgp_vigembus_message}. "
                "This build will not ship a driver installer; set VGP_VIGEMBUS_INSTALLER "
                "to a local copy, or build with network access, to bundle one."
            )
        else()
            string(TOLOWER "${VGP_VIGEMBUS_SHA512}" vgp_vigembus_expected)
            file(SHA512 "${VGP_VIGEMBUS_INSTALLER}" vgp_vigembus_actual)
            if(NOT vgp_vigembus_actual STREQUAL vgp_vigembus_expected)
                file(REMOVE "${VGP_VIGEMBUS_INSTALLER}")
                message(WARNING
                    "The downloaded ViGEmBus installer did not match its expected "
                    "SHA-512 and was discarded (expected ${vgp_vigembus_expected}, "
                    "got ${vgp_vigembus_actual}). This build will not ship a driver "
                    "installer."
                )
            endif()
        endif()
    endif()

    if(EXISTS "${VGP_VIGEMBUS_INSTALLER}")
        message(STATUS "Bundling the ViGEmBus ${VGP_VIGEMBUS_VERSION} installer")
    else()
        message(STATUS "No ViGEmBus installer will be bundled")
    endif()
endif()

# --- ViGEmClient: the user-mode library that creates the virtual pad ---------
#
# `ViGEmGamepad` resolves the `vigem_*` entry points from `ViGEmClient.dll` at
# run time. The driver installer above does not contain it (ViGEmBus is the
# kernel driver), and a clean Windows machine does not have it either, so with
# nothing to load the pad was never created: every program's input-device list
# stayed empty while the app itself ran normally. The library is therefore
# built here from the pinned upstream source and installed beside the
# executable — no binary blob is checked into the repository, and the
# checksum pin keeps the source reproducible.
#
# The download follows the same rules as the driver above: every failure is a
# warning, never a broken configure, and the archive is verified before it is
# unpacked. What makes a missing library impossible to ship silently is the
# check in the installer step of `.github/workflows/extended-project-ci.yml`.
if(WIN32)
option(VGP_BUNDLE_VIGEMCLIENT "Build and ship ViGEmClient.dll with the application" ON)

set(VGP_VIGEMCLIENT_VERSION "1.21.222.0" CACHE STRING "ViGEmClient release to build")
set(VGP_VIGEMCLIENT_SHA512
    "a2402624a8f131936d7d7a5b4ef98358f589669d5664ba3431b3e898378ae1e3936b58ad30905d9cff7aa92244c67180a29cdb0fc0766dc3eb87ba1fee41cf9e"
    CACHE STRING "SHA-512 of the ViGEmClient source archive"
)
# A cache entry so an offline build can point this at a local copy and still
# ship the library, mirroring VGP_VIGEMBUS_INSTALLER.
set(VGP_VIGEMCLIENT_ARCHIVE
    "${CMAKE_BINARY_DIR}/vigemclient/ViGEmClient-${VGP_VIGEMCLIENT_VERSION}.tar.gz"
    CACHE FILEPATH "ViGEmClient source archive to build the shipped library from"
)
set(VGP_VIGEMCLIENT_DIR "${CMAKE_BINARY_DIR}/vigemclient/ViGEmClient-${VGP_VIGEMCLIENT_VERSION}")

if(VGP_BUNDLE_VIGEMCLIENT AND NOT EXISTS "${VGP_VIGEMCLIENT_DIR}/src/ViGEmClient.cpp")
    get_filename_component(vgp_vigemclient_archive_dir "${VGP_VIGEMCLIENT_ARCHIVE}" DIRECTORY)
    file(MAKE_DIRECTORY "${vgp_vigemclient_archive_dir}")

    if(NOT EXISTS "${VGP_VIGEMCLIENT_ARCHIVE}")
        file(DOWNLOAD
            "https://github.com/nefarius/ViGEmClient/archive/refs/tags/v${VGP_VIGEMCLIENT_VERSION}.tar.gz"
            "${VGP_VIGEMCLIENT_ARCHIVE}"
            TLS_VERIFY ON
            STATUS vgp_vigemclient_download
        )
        list(GET vgp_vigemclient_download 0 vgp_vigemclient_code)
        if(NOT vgp_vigemclient_code EQUAL 0)
            list(GET vgp_vigemclient_download 1 vgp_vigemclient_message)
            file(REMOVE "${VGP_VIGEMCLIENT_ARCHIVE}")
            message(WARNING
                "Could not download ViGEmClient ${VGP_VIGEMCLIENT_VERSION}: "
                "${vgp_vigemclient_message}. No ViGEmClient.dll will be built, so the "
                "shipped application would create no controller. Set "
                "VGP_VIGEMCLIENT_ARCHIVE to a local copy of the archive, or build with "
                "network access."
            )
        endif()
    endif()

    if(EXISTS "${VGP_VIGEMCLIENT_ARCHIVE}")
        string(TOLOWER "${VGP_VIGEMCLIENT_SHA512}" vgp_vigemclient_expected)
        file(SHA512 "${VGP_VIGEMCLIENT_ARCHIVE}" vgp_vigemclient_actual)
        if(NOT vgp_vigemclient_actual STREQUAL vgp_vigemclient_expected)
            file(REMOVE "${VGP_VIGEMCLIENT_ARCHIVE}")
            message(WARNING
                "The ViGEmClient archive did not match its expected SHA-512 and was "
                "discarded (expected ${vgp_vigemclient_expected}, got "
                "${vgp_vigemclient_actual}). No ViGEmClient.dll will be built."
            )
        else()
            file(ARCHIVE_EXTRACT INPUT "${VGP_VIGEMCLIENT_ARCHIVE}"
                DESTINATION "${CMAKE_BINARY_DIR}/vigemclient"
            )
        endif()
    endif()
endif()

if(EXISTS "${VGP_VIGEMCLIENT_DIR}/src/ViGEmClient.cpp")
    message(STATUS "Building ViGEmClient ${VGP_VIGEMCLIENT_VERSION} as ViGEmClient.dll")
    add_library(VGP_ViGEmClient SHARED "${VGP_VIGEMCLIENT_DIR}/src/ViGEmClient.cpp")

    # `VIGEM_DYNAMIC` and `VIGEM_EXPORTS` are what turn `VIGEM_API` into
    # `__declspec(dllexport)`; the upstream sources define neither on their own.
    # Without them the DLL would compile but export no `vigem_*` function, and
    # the pad would silently never appear. The `extern "C"` declarations in
    # `Client.h` keep the exported names exactly what `GetProcAddress` reads.
    target_compile_definitions(VGP_ViGEmClient PRIVATE VIGEM_DYNAMIC VIGEM_EXPORTS)
    target_include_directories(VGP_ViGEmClient PRIVATE
        "${VGP_VIGEMCLIENT_DIR}/include"
        "${VGP_VIGEMCLIENT_DIR}/src"
    )
    # The only system library the upstream shared build links as well.
    target_link_libraries(VGP_ViGEmClient PRIVATE setupapi)
    set_target_properties(VGP_ViGEmClient PROPERTIES
        OUTPUT_NAME ViGEmClient
        AUTOMOC OFF
        AUTOUIC OFF
        AUTORCC OFF
    )
else()
    message(WARNING
        "The ViGEmClient sources are not available, so no ViGEmClient.dll will "
        "be built: the shipped application would fall back to the input-injection "
        "API, which games and emulators never list as a controller. Build with "
        "network access (or set VGP_VIGEMCLIENT_ARCHIVE) to fetch "
        "ViGEmClient ${VGP_VIGEMCLIENT_VERSION}."
    )
endif()
endif() # WIN32

add_subdirectory(VGP_Data_Exchange/C)

set(QR_CODE_GEN_SOURCES
    third-party-libs/QR-Code-generator/cpp/qrcodegen.cpp
    third-party-libs/QR-Code-generator/cpp/qrcodegen.hpp
)

add_library(QR_Code_Generator SHARED ${QR_CODE_GEN_SOURCES})

if(WIN32)
    # qrcodegen has no dllexport annotations, so MSVC produces a DLL with no
    # import library and the executable link fails with LNK1181. Auto-export
    # every symbol so a QR_Code_Generator.lib is generated (MinGW and ELF link
    # shared objects without an import library, which masked this on other hosts).
    set_target_properties(QR_Code_Generator PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()

# Manifest file for the application
# This file is used to request permissions on Windows
SET(APP_MANIFEST_FILE "${CMAKE_SOURCE_DIR}/res/VGamepadPC.exe.manifest")

# Add project files sorted in alphabetical order
set(PROJECT_SOURCES
    res/icons.qrc
    src/appdir.hpp
    src/crash_report.cpp
    src/crash_report.hpp
    src/main.cpp
    src/networking/controller_test_state.cpp
    src/networking/controller_test_state.hpp
    src/networking/executor.cpp
    src/networking/executor.hpp
    src/networking/input_backend_status.cpp
    src/networking/input_backend_status.hpp
    src/networking/server.cpp
    src/networking/server.hpp
    src/networking/server.ui
    src/networking/usb_bridge.cpp
    src/networking/usb_bridge.hpp
    src/settings/settings.hpp
    src/settings/settings_singleton.cpp
    src/settings/settings_singleton.hpp
    src/settings/game_layout.cpp
    src/settings/game_layout.hpp
    src/settings/key_names.cpp
    src/settings/key_names.hpp
    src/settings/keymap_profile.hpp
    src/settings/keymap_profile.cpp
    src/simulation/gamepadSim.hpp
    src/simulation/keyboardSim.hpp
    src/simulation/mouseSim.hpp
    src/update/update_checker.cpp
    src/update/update_checker.hpp
    src/ui/about.cpp
    src/ui/about.hpp
    src/ui/about.ui
    src/ui/badge.cpp
    src/ui/badge.hpp
    src/ui/buttoninputbox.cpp
    src/ui/buttoninputbox.hpp
    src/ui/controllertest.cpp
    src/ui/controllertest.hpp
    src/ui/gamepaddiagram.cpp
    src/ui/gamepaddiagram.hpp
    src/ui/homepage.cpp
    src/ui/homepage.hpp
    src/ui/keymappage.cpp
    src/ui/keymappage.hpp
    src/ui/layoutspage.cpp
    src/ui/layoutspage.hpp
    src/ui/mainwindow.cpp
    src/ui/mainwindow.hpp
    src/ui/mainwindow.ui
    src/ui/preferences.cpp
    src/ui/preferences.hpp
    src/ui/preferences.ui
    src/ui/shell.cpp
    src/ui/shell.hpp
    src/ui/theme.cpp
    src/ui/theme.hpp
    src/ui/usbpage.cpp
    src/ui/usbpage.hpp
)

# Platform-specific simulation sources
if(WIN32)
    list(APPEND PROJECT_SOURCES
        src/platform/windows/console.cpp
        src/platform/windows/console.hpp
        src/simulation/windows/gamepadSim.cpp
        src/simulation/windows/keyboardSim.cpp
        src/simulation/windows/mouseSim.cpp
        src/simulation/windows/vigemClient.cpp
        src/simulation/windows/vigemClient.hpp
        src/platform/windows/vigemBusInstaller.cpp
        src/platform/windows/vigemBusInstaller.hpp
    )
elseif(LINUX)
    list(APPEND PROJECT_SOURCES
        src/simulation/linux/gamepadSim.cpp
        src/simulation/linux/keyboardSim.cpp
        src/simulation/linux/mouseSim.cpp
    )
endif()

qt_add_executable(VGamepadPC
    ${PROJECT_SOURCES}
)

target_link_libraries(VGamepadPC PRIVATE
    Qt${QT_VERSION_MAJOR}::Core
    Qt${QT_VERSION_MAJOR}::Widgets
    Qt${QT_VERSION_MAJOR}::Network
    Data_Exchange
    QR_Code_Generator
)

# Define portable build mode
if(PORTABLE_BUILD)
    target_compile_definitions(VGamepadPC PRIVATE PORTABLE_BUILD=1)
    message(STATUS "Building in PORTABLE mode - data stored alongside executable")
else()
    message(STATUS "Building in INSTALLABLE mode - data stored in standard OS locations")
endif()

# Platform-specific linking and include directories
if(WIN32)
    target_link_libraries(VGamepadPC PRIVATE
        cppwinrt
        WindowsApp
    )
    # Windows.h defines min/max macros that break std::min/std::max.
    target_compile_definitions(VGamepadPC PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
elseif(LINUX)
    target_link_libraries(VGamepadPC PRIVATE
        ${UINPUT_LIBRARIES}
    )
    target_include_directories(VGamepadPC PRIVATE
        ${UINPUT_INCLUDE_DIRS}
    )
endif()

# Apply hardening
openssf_harden_target(VGamepadPC)

set_target_properties(VGamepadPC PROPERTIES
    WIN32_EXECUTABLE TRUE
)

set(CMAKE_INSTALL_DIR "${CMAKE_SOURCE_DIR}/dist")

install(TARGETS VGamepadPC Data_Exchange QR_Code_Generator
    BUNDLE DESTINATION .
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
)

# Platform-specific resources/tools
if(WIN32)
    # Manifest file for the application
    # This file is used to request permissions on Windows
    SET(APP_MANIFEST_FILE "${CMAKE_SOURCE_DIR}/res/VGamepadPC.exe.manifest")

    # Windows deployment tool
    if(NOT DEFINED WINDEPLOYQT_EXECUTABLE)
        # Find the windeployqt executable in the Qt installation path
        # This is typically located in the bin directory of the Qt installation
        # Adjust the path based on your Qt installation
        find_program(WINDEPLOYQT_EXECUTABLE windeployqt HINTS ${CMAKE_PREFIX_PATH}/bin)
    endif()
    if(WINDEPLOYQT_EXECUTABLE)
        message(STATUS "Found windeployqt: ${WINDEPLOYQT_EXECUTABLE}")
    endif()
endif()

# Platform-specific installation
if(WIN32)
    install(FILES "${APP_MANIFEST_FILE}" 
        DESTINATION "${CMAKE_INSTALL_BINDIR}"
    )

    # The app looks for this beside itself, so it has to land in the same
    # directory as the executable. When the download was skipped there is
    # nothing to install and the app explains where to get the driver instead.
    if(EXISTS "${VGP_VIGEMBUS_INSTALLER}")
        install(FILES "${VGP_VIGEMBUS_INSTALLER}"
            DESTINATION "${CMAKE_INSTALL_BINDIR}"
        )
    endif()

    # Same rule for the library the pad is created through: the app loads it
    # from beside the executable, so it installs into the very directory the
    # installer packs. Without it there is no virtual controller on any machine
    # that does not happen to have one from another tool.
    if(TARGET VGP_ViGEmClient)
        install(TARGETS VGP_ViGEmClient
            RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
        )
    endif()

    install(
        CODE "execute_process(COMMAND \"${WINDEPLOYQT_EXECUTABLE}\" \"\${CMAKE_INSTALL_PREFIX}/${CMAKE_INSTALL_BINDIR}/VGamepadPC.exe\")"
    )
elseif(LINUX)
    # https://cmake.org/cmake/help/latest/prop_tgt/INSTALL_RPATH.html
    set_target_properties(VGamepadPC PROPERTIES
        INSTALL_RPATH_USE_LINK_PATH TRUE
        INSTALL_RPATH "$ORIGIN/../lib"
        BUILD_WITH_INSTALL_RPATH FALSE
    )
    # Qt's deployment-script API and package metadata differ across minor releases.
    # Keep source builds usable with distro Qt packages; use the deploy script on Qt 6.8+.
    if(Qt6Core_VERSION VERSION_GREATER_EQUAL "6.8.0")
        qt_generate_deploy_app_script(
            TARGET VGamepadPC
            OUTPUT_SCRIPT deploy_script
        )
        message(STATUS "Deploy script generated: ${deploy_script}")
        install(SCRIPT ${deploy_script})
    else()
        message(STATUS "Skipping Qt deploy script for Qt ${Qt6Core_VERSION}; use Qt 6.8+ for packaged deployment.")
    endif()
endif()

# Platform-specific post-build steps
if(WIN32)
    # On Windows, we need to copy the DLLs to the output directory using windeployqt
    # This is done by adding a custom command as a post-build step
    # The CMake Cache variable WINDEPLOYQT_EXECUTABLE should contain the path to windeployqt
    add_custom_command(
        TARGET VGamepadPC POST_BUILD
        COMMAND ${WINDEPLOYQT_EXECUTABLE} $<TARGET_FILE:VGamepadPC>
        COMMENT "Running windeployqt to copy Qt dependencies to output directory"
        VERBATIM
    )

    # Configure the executable to use the manifest
    # Just place the manifest in the output directory and Windows will use it
    add_custom_command(
        TARGET VGamepadPC POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            ${APP_MANIFEST_FILE}
            $<TARGET_FILE_DIR:VGamepadPC>/VGamepadPC.exe.manifest
        COMMENT "Copying manifest file to output directory"
        VERBATIM
    )

    # Ship the driver installer beside the executable, which is where the app
    # looks for it before offering to install the driver.
    if(EXISTS "${VGP_VIGEMBUS_INSTALLER}")
        add_custom_command(
            TARGET VGamepadPC POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                "${VGP_VIGEMBUS_INSTALLER}"
                "$<TARGET_FILE_DIR:VGamepadPC>/${VGP_VIGEMBUS_ASSET}"
            COMMENT "Copying the ViGEmBus installer next to the application"
            VERBATIM
        )
    endif()
endif()
