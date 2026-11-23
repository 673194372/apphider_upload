package com.hider.applist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * 配置界面。
 *
 * 核心功能：导入 HMA 格式的 JSON 配置。
 * 导入后写到 /sdcard/Android/data/com.hider.applist/files/config.json，
 * 模块在目标 App 进程里启动时自动读取。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private TextView status;
    private TextView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("应用列表隐藏器");
        title.setTextSize(20);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setTextSize(12);
        desc.setText("在 FPA 里 patch 目标 App 时勾选本模块即可生效。\n"
                + "支持导入 HMA（Hide My Applist）导出的 JSON 配置。");
        desc.setPadding(0, dp(8), 0, dp(16));
        root.addView(desc);

        Button importBtn = new Button(this);
        importBtn.setText("① 导入 HMA 配置 JSON");
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });
        root.addView(importBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("清除导入的配置（回到内置默认）");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCfg();
            }
        });
        root.addView(clearBtn);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("刷新状态");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        root.addView(refreshBtn);

        status = new TextView(this);
        status.setTextSize(12);
        status.setPadding(0, dp(16), 0, dp(8));
        root.addView(status);

        preview = new TextView(this);
        preview.setTextSize(10);
        preview.setPadding(0, dp(8), 0, 0);
        root.addView(preview);

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ==================== 导入 ====================

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选择 HMA 配置 JSON"), REQ_PICK);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            String text = readUri(uri);
            // 先验证能解析
            ConfigLoader.Result r = parseForPreview(text);
            if (r == null || r.isEmpty()) {
                Toast.makeText(this, "解析失败：不是有效的 HMA 配置", Toast.LENGTH_LONG).show();
                return;
            }
            File dst = targetFile();
            dst.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(dst)) {
                os.write(text.getBytes("UTF-8"));
            }
            Toast.makeText(this,
                    "导入成功\n模式: " + (r.isWhitelist ? "白名单" : "黑名单")
                            + "\n条目: " + (r.isWhitelist ? r.visible.size() : r.hidden.size()),
                    Toast.LENGTH_LONG).show();
            refresh();
        } catch (Throwable t) {
            Toast.makeText(this, "导入失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readUri(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** 借用 ConfigLoader 的解析逻辑做预览（不写文件） */
    private ConfigLoader.Result parseForPreview(String text) {
        try {
            // ConfigLoader.parseHmaJson 是 private，这里通过反射调用
            java.lang.reflect.Method m = ConfigLoader.class.getDeclaredMethod(
                    "parseHmaJson", String.class, String.class);
            m.setAccessible(true);
            return (ConfigLoader.Result) m.invoke(null, text, "(导入预览)");
        } catch (Throwable t) {
            return null;
        }
    }

    private File targetFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = new File(Environment.getExternalStorageDirectory(),
                    "Android/data/" + getPackageName() + "/files");
        }
        return new File(dir, "config.json");
    }

    private void clearCfg() {
        File f = targetFile();
        if (f.exists() && f.delete()) {
            Toast.makeText(this, "已删除，将使用内置默认列表", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可清除的配置", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    // ==================== 状态显示 ====================

    private void refresh() {
        File f = targetFile();
        StringBuilder sb = new StringBuilder();

        if (f.exists()) {
            sb.append("✅ 已导入配置\n");
            sb.append("路径: ").append(f.getAbsolutePath()).append("\n");
            sb.append("大小: ").append(f.length()).append(" 字节\n");
            try {
                String text = readFile(f);
                ConfigLoader.Result r = parseForPreview(text);
                if (r != null) {
                    sb.append("模式: ").append(r.isWhitelist ? "白名单" : "黑名单").append("\n");
                    sb.append("条目: ").append(r.isWhitelist ? r.visible.size() : r.hidden.size()).append("\n");
                }
            } catch (Throwable t) {
                sb.append("读取失败: ").append(t.getMessage()).append("\n");
            }
        } else {
            sb.append("⚠️ 未导入配置，使用内置默认黑名单（")
                    .append(Config.DEFAULT_HIDDEN.length).append(" 项）\n");
        }

        status.setText(sb.toString());
        preview.setText(buildPreview());
    }

    private String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private String buildPreview() {
        StringBuilder sb = new StringBuilder();
        File f = targetFile();
        if (!f.exists()) {
            sb.append("内置默认列表：\n");
            for (String s : Config.DEFAULT_HIDDEN) {
                sb.append("  ").append(s).append("\n");
            }
            return sb.toString();
        }
        try {
            ConfigLoader.Result r = parseForPreview(readFile(f));
            if (r == null) {
                return "解析失败";
            }
            sb.append(r.isWhitelist ? "白名单（只允许这些可见）：\n" : "黑名单（隐藏这些）：\n");
            java.util.Set<String> set = r.isWhitelist ? r.visible : r.hidden;
            int i = 0;
            for (String s : set) {
                if (i++ >= 50) {
                    sb.append("  ... 共 ").append(set.size()).append(" 项\n");
                    break;
                }
                sb.append("  ").append(s).append("\n");
            }
        } catch (Throwable t) {
            sb.append("预览失败: ").append(t.getMessage());
        }
        return sb.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
