cmake_minimum_required(VERSION 3.22.1)

project("lsinputd")

add_executable(lsinputd lsinputd.c)

set_target_properties(lsinputd PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    OUTPUT_NAME "lsinputd"
)

target_link_options(lsinputd PRIVATE -pie -Wl,-z,max-page-size=16384)
