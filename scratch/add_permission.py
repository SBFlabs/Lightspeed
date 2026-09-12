with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

# Insert after the last permission
import re
last_perm_match = list(re.finditer(r'<uses-permission[^>]+/>', content))[-1]
insert_pos = last_perm_match.end()

new_content = content[:insert_pos] + '\n    <uses-permission android:name="android.permission.CALL_PHONE" />' + content[insert_pos:]

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(new_content)
