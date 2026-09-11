import re
import sys

# I will write a simple script that replaces the static Column with a dynamic one for a given section.
# But since each section has different variable names (isTopExpanded, isTopGeoExpanded, etc.),
# it's better to just use replace_file_content with targeted replacements for each block.

