# OpenSSFHardening.cmake
#
# CMake module to apply OpenSSF-recommended compiler and linker hardening.
# https://best.openssf.org/Compiler-Hardening-Guides/Compiler-Options-Hardening-Guide-for-C-and-C++
#
# Usage:
#   include(OpenSSFHardening)
#   add_executable(myApp main.cpp) # Or add_library(myLib ...)
#   openssf_harden_target(myApp)

# Prevent multiple inclusion
if(DEFINED OpenSSF_Hardening_INCLUDED)
	return()
endif()
set(OpenSSF_Hardening_INCLUDED TRUE)

# Ensure target_link_options() is available (CMake ≥ 3.13)
if(NOT COMMAND target_link_options)
	message(FATAL_ERROR "OpenSSFHardening.cmake requires CMake ≥ 3.13 for target_link_options()")
endif()

# The GNU/Clang flag set is not accepted by MSVC's cl.exe (which fails with
# errors such as "D8021: invalid numeric argument '/Wextra'"), so the flags are
# selected per compiler family.

if(MSVC)
	# MSVC enables /GS, /DYNAMICBASE and /NXCOMPAT by default; make the intent
	# explicit and add Control Flow Guard plus an elevated warning level.
	set(OpenSSF_Hardening_FLAGS
		/W4
		/guard:cf
	)
	set(OpenSSF_Hardening_LINK_FLAGS
		/guard:cf
		/DYNAMICBASE
		/HIGHENTROPYVA
		/NXCOMPAT
	)
	set(OpenSSF_Hardening_C_FLAGS)
	# _FORTIFY_SOURCE and _GLIBCXX_ASSERTIONS are glibc/libstdc++ specific.
	set(OpenSSF_Hardening_RELEASE_DEFS)
else()
	# 1. Feature tests
	include(CheckCXXCompilerFlag)
	include(CheckLinkerFlag)
	check_cxx_compiler_flag("-ftrivial-auto-var-init=zero"    OpenSSF_HARDENING_HAS_TRIVIAL_AUTO_INIT)
	check_cxx_compiler_flag("-fno-delete-null-pointer-checks" OpenSSF_HARDENING_HAS_NO_DELETE_NULL)
	check_cxx_compiler_flag("-fhardened"                      OpenSSF_HARDENING_HAS_HARDENED)
	check_cxx_compiler_flag("-fsanitize=undefined"            OpenSSF_HARDENING_HAS_UB_SAN)
	check_linker_flag(CXX "-fsanitize=undefined"              OpenSSF_HARDENING_LINK_HAS_UB_SAN)

	# 2. Base hardening flags (C & C++)
	set(OpenSSF_Hardening_FLAGS
		-Wall
		-Wextra
		-Wpedantic
		-Wformat=2
		-Wconversion
		-Wimplicit-fallthrough
		-Wnull-dereference
		-Werror=format-security
		-fstack-clash-protection
		-fstack-protector-strong
		-fno-strict-aliasing
		-fno-strict-overflow
	)
	set(OpenSSF_Hardening_LINK_FLAGS)

	if(OpenSSF_HARDENING_HAS_TRIVIAL_AUTO_INIT)
		list(APPEND OpenSSF_Hardening_FLAGS
			-ftrivial-auto-var-init=zero
		)
	endif()
	if(OpenSSF_HARDENING_HAS_NO_DELETE_NULL)
		list(APPEND OpenSSF_Hardening_FLAGS
			-fno-delete-null-pointer-checks
		)
	endif()
	if(OpenSSF_HARDENING_HAS_HARDENED)
		list(APPEND OpenSSF_Hardening_FLAGS
			-fhardened
		)
	endif()
	if(OpenSSF_HARDENING_HAS_UB_SAN AND OpenSSF_HARDENING_LINK_HAS_UB_SAN)
		list(APPEND OpenSSF_Hardening_FLAGS
			-fsanitize=undefined
		)
		list(APPEND OpenSSF_Hardening_LINK_FLAGS
			-fsanitize=undefined
		)
	endif()

	# 3. Compiler-version-specific flags
	if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
		if(CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL "13.0")
			list(APPEND OpenSSF_Hardening_FLAGS -fstrict-flex-arrays=3)
		endif()
		if(CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL "15.0")
			list(APPEND OpenSSF_Hardening_FLAGS -fzero-init-padding-bits=all)
		endif()
	elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
		if(CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL "16.0")
			list(APPEND OpenSSF_Hardening_FLAGS -fstrict-flex-arrays=3)
		endif()
	endif()

	# 4. C-only hardening flags
	set(OpenSSF_Hardening_C_FLAGS
		-Werror=implicit
		-Werror=incompatible-pointer-types
		-Werror=int-conversion
	)

	# 5. Linker hardening flags - platform specific setup (GNU ld compatible)
	if(CMAKE_SYSTEM_NAME STREQUAL "Linux" OR CMAKE_SYSTEM_NAME STREQUAL "Windows")
		list(APPEND OpenSSF_Hardening_LINK_FLAGS
			-Wl,--as-needed
			-Wl,--no-copy-dt-needed-entries
		)

		if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
			list(APPEND OpenSSF_Hardening_LINK_FLAGS
				-Wl,-z,nodlopen
				-Wl,-z,noexecstack
				-Wl,-z,relro
				-Wl,-z,now
			)
		endif()
	endif()

	# 6. Release-only defines (glibc/libstdc++ specific)
	set(OpenSSF_Hardening_RELEASE_DEFS
		_FORTIFY_SOURCE=3
		_GLIBCXX_ASSERTIONS
	)
endif()

# 7. Function: openssf_harden_target
function(openssf_harden_target target)
	# 7.1 Apply compiler hardening flags
	target_compile_options(${target} PRIVATE
		${OpenSSF_Hardening_FLAGS}
		$<$<COMPILE_LANGUAGE:C>:${OpenSSF_Hardening_C_FLAGS}>
	)

	# 7.2 Enable PIE/PIC
	set_target_properties(${target} PROPERTIES
		POSITION_INDEPENDENT_CODE ON
	)

	# 7.3 Apply linker hardening flags
	target_link_options(${target} PRIVATE
		${OpenSSF_Hardening_LINK_FLAGS}
	)

	# 7.4 Release-only defines (empty on MSVC)
	if(OpenSSF_Hardening_RELEASE_DEFS)
		target_compile_definitions(${target} PRIVATE
			$<$<CONFIG:Release>:${OpenSSF_Hardening_RELEASE_DEFS}>
		)
	endif()
endfunction()
