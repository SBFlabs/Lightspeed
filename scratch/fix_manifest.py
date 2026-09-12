import re
with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

content = re.sub(r'<activity[^>]*OmniscientAudioDockActivity[^>]*>.*?</activity>', '', content, flags=re.DOTALL)
content = re.sub(r'<activity[^>]*OmniscientAudioDockActivity[^>]*/>', '', content)

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)

