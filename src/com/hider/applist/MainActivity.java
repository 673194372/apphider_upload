package com.hider.applist;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 只读信息界面。
 *
 * 配置已编译进 dex（WhitelistData），无法在运行时修改。
 * 要改配置：编辑 JSON → 重新生成 → 重新构建 APK。
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
        title.setText("应用列表隐藏器");
        title.setTextSize(20);
        root.addView(title);

        TextView info = new TextView(this);
        info.setTextSize(12);
        info.setPadding(0, dp(12), 0, dp(12));

        StringBuilder sb = new StringBuilder();
        sb.append("配置来源: 编译内置\n");
        sb.append("模式: ").append(WhitelistData.IS_WHITELIST ? "白名单" : "黑名单").append("\n");
        sb.append("条目数: ").append(WhitelistData.PACKAGES.length).append("\n\n");

        if (WhitelistData.IS_WHITELIST) {
            sb.append("白名单语义：只有列表里的 App 能被【目标 App】看到，\n");
            sb.append("不在列表里的全部隐藏。\n\n");
        } else {
            sb.append("黑名单语义：列表里的 App 会被隐藏。\n\n");
        }

        sb.append("使用方式：\n");
        sb.append("1. 在 FPA 里 patch 目标 App 时勾选本模块\n");
        sb.append("2. 装 patched 版 App\n");
        sb.append("3. 打开后即生效\n\n");

        sb.append("改配置方法：\n");
        sb.append("修改 JSON → 重新生成 WhitelistData.java → 重新构建\n");
        sb.append("（Android 11+ 分区存储下，运行时读外部配置会被拒绝）\n\n");

        sb.append("前 30 项预览：\n");
        int n = Math.min(30, WhitelistData.PACKAGES.length);
        for (int i = 0; i < n; i++) {
            sb.append("  ").append(WhitelistData.PACKAGES[i]).append("\n");
        }
        if (WhitelistData.PACKAGES.length > n) {
            sb.append("  ... 共 ").append(WhitelistData.PACKAGES.length).append(" 项\n");
        }

        info.setText(sb.toString());
        root.addView(info);

        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
