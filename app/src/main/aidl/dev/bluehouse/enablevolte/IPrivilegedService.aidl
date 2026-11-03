package dev.bluehouse.enablevolte;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;

interface IPrivilegedService {
    IBinder getSystemService(String name);
    int getServiceUid();
    String getAllowedSystemProperty(String name);
    boolean setAllowedSystemProperty(String name, String value);
    String getTelephonyDiagnosticSnapshot(String kind);
    String getRegionalModemPatchStatus();
    String installRegionalModemPatch();
    String scheduleRegionalModemPatchRemoval();
    int getWifiEnabledState();
    boolean setWifiEnabled(boolean enabled);
    String getRootVoWifiStatus(int subscriptionId);
    String applyRootVoWifiRepair(int subscriptionId);
    String restoreRootVoWifiRepair(int subscriptionId);
    boolean setImsStatusBarMonitoring(boolean enabled, in int[] subscriptionIds);
    String captureRadioLogs(in ParcelFileDescriptor output, boolean redact);
}
