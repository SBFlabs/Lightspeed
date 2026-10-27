package com.sbf.lightspeed.system.sonic;

interface ISonicIsolationUserService {
    void initialize(String appPackageName);
    void registerClientToken(IBinder token);
    void startPipeline();
    void stopPipeline();
    boolean isPipelineRunning();
    void setVocalBoostLevel(float level);
    void setBackgroundMuffleLevel(float level);
    void setAppRoute(int uid, String route);
    void clearAppRoute(int uid);
    void clearAllAppRoutes();
    List<String> getActiveAudioPackages();
    boolean setPackageVolume(String packageName, float volume);
    void resetAllPackageVolumes();
    void setMultiAudioFocus(boolean enabled);
    void destroy();
}
