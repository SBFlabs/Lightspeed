import re

with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

activity_block = """        <activity
            android:name=".system.OmniscientAudioDockActivity"
            android:exported="true"
            android:theme="@style/Theme.Lightspeed.Transparent"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="com.sbf.lightspeed.OMNISCIENT_AUDIO" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <activity
            android:name=".system.TacticalFlyoutActivity\""""[:-1]

content = content.replace('        <activity\n            android:name=".system.TacticalFlyoutActivity"', activity_block)

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)
