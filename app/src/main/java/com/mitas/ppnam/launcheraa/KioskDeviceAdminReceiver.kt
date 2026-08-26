package com.mitas.ppnam.launcheraa

import android.app.admin.DeviceAdminReceiver

/**
 * Required hook for device-owner provisioning. All policy is applied by [KioskManager];
 * this receiver only has to exist (and be declared in the manifest) so
 * `dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver` has a target.
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver()
