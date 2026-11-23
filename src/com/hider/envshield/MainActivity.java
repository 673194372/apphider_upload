package com.hider.envshield;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 说明界面。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("设备环境盾");
        title.setTextSize(22);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v1.0 · 免 root 环境检测拦截");
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        TextView body = new TextView(this);
        body.setTextSize(13);
        body.setText(buildText());
        root.addView(body);

        scroll.addView(root);
        setContentView(scroll);
    }

    private String buildText() {
        Config cfg = Config.load();
        StringBuilder sb = new StringBuilder();

        sb.append("作用范围（当前配置）：\n\n");

        sb.append(cfg.hideDevOptions ? "✅" : "❌").append(" 开发者选项 / USB 调试检测\n");
        sb.append("      · adb_enabled、development_settings_enabled\n");
        sb.append("      · adb_wifi_enabled（无线调试）、adb_tcp_port\n");
        sb.append("      · ro.debuggable / ro.adb.secure / init.svc.adbd\n\n");

        sb.append(cfg.hideRoot ? "✅" : "❌").append(" Root 检测\n");
        sb.append("      · su / magisk / SuperSU 路径\n");
        sb.append("      · Runtime.exec(\"su\")\n");
        sb.append("      · Build.TAGS = test-keys\n\n");

        sb.append(cfg.hideRootApps ? "✅" : "❌").append(" root 管理应用包名\n");
        sb.append("      · Magisk Manager / SuperSU / KingRoot 等\n\n");

        sb.append(cfg.hideHook ? "✅" : "❌").append(" Hook / Xposed 检测\n");
        sb.append("      · Class.forName 找 Xposed 类\n");
        sb.append("      · StackTrace 里的框架帧\n\n");

        sb.append(cfg.hideFrida ? "✅" : "❌").append(" Frida 检测\n\n");

        sb.append(cfg.hideEmulator ? "✅" : "❌").append(" 模拟器检测\n");
        sb.append("      · qemu / goldfish 路径与属性\n");
        sb.append("      · Build.FINGERPRINT / MODEL 等\n\n");

        sb.append(cfg.hideAccessibility ? "✅" : "❌").append(" 无障碍服务检测\n\n");
        sb.append(cfg.hideProxy ? "✅" : "❌").append(" 抓包代理检测\n\n");
        sb.append(cfg.hideBluetoothMac ? "✅" : "❌").append(" 蓝牙 / MAC 检测\n\n");

        sb.append("使用步骤：\n");
        sb.append("1. 在 FPA 里 patch 目标 App 时勾选本模块；\n");
        sb.append("2. 装 patched 版；\n");
        sb.append("3. 强行停止目标应用后重新打开。\n\n");

        sb.append("提示：本模块只影响目标应用进程，\n");
        sb.append("不会修改系统设置或其他应用。");

        return sb.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
