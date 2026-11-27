cmake_minimum_required(VERSION 3.22.1)

project("lsinputd")

add_executable(lsinputd lsinputd.c)

set_target_properties(lsinputd PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    OUTPUT_NAME "lsinputd"
)

target_link_options(lsinputd PRIVATE -pie -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)

add_library(sonicdsp SHARED sonic_dsp.cpp)
target_compile_options(sonicdsp PRIVATE -O3 -ffast-math -fno-exceptions -fno-rtti)
target_link_options(sonicdsp PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
