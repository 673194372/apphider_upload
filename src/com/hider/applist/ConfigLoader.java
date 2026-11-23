package com.hider.applist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/**
 * 配置加载器。
 *
 * 支持两种来源，优先级从高到低：
 *   1) 外部 JSON 文件（兼容 HMA 导出格式）
 *      - /sdcard/Android/data/com.hider.applist/files/config.json
 *      - /sdcard/apphider.json
 *      - /data/local/tmp/apphider.json
 *   2) 内置默认黑名单
 */
final class ConfigLoader {

    private static final String TAG = "[AppHider/Cfg]";

    private static final String[] CFG_PATHS = {
            "/sdcard/Android/data/com.hider.applist/files/config.json",
            "/storage/emulated/0/Android/data/com.hider.applist/files/config.json",
            "/sdcard/apphider.json",
            "/storage/emulated/0/apphider.json",
            "/data/local/tmp/apphider.json",
    };

    private ConfigLoader() {
    }

    static final class Result {
        final Set<String> hidden = new LinkedHashSet<String>();
        final Set<String> visible = new LinkedHashSet<String>();
        boolean isWhitelist = false;
        String source = "内置默认";

        boolean isEmpty() {
            return hidden.isEmpty() && visible.isEmpty();
        }
    }

    static Result load() {
        Result r = loadFromJsonFile();
        if (r != null && !r.isEmpty()) {
            XposedBridge.log(TAG + " 从 " + r.source + " 加载成功");
            return r;
        }

        Result def = new Result();
        for (String p : Config.DEFAULT_HIDDEN) {
            def.hidden.add(p);
        }
        def.source = "内置默认";
        XposedBridge.log(TAG + " 使用内置默认列表，共 " + def.hidden.size() + " 项");
        return def;
    }

    private static Result loadFromJsonFile() {
        for (String path : CFG_PATHS) {
            try {
                File f = new File(path);
                if (!f.exists() || !f.canRead() || f.length() < 10) {
                    continue;
                }
                String text = readAll(f);
                Result r = parseHmaJson(text, path);
                if (r != null && !r.isEmpty()) {
                    return r;
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + " 读 " + path + " 失败: " + t.getMessage());
            }
        }
        return null;
    }

    private static String readAll(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return sb.toString();
    }

    static Result parseHmaJson(String text, String path) {
        try {
            String tplJson = extractObject(text, "templates");
            if (tplJson == null) {
                XposedBridge.log(TAG + " JSON 里没有 templates");
                return null;
            }

            String tplName = firstKey(tplJson);
            if (tplName == null) {
                return null;
            }
            String tplBody = extractObject(tplJson, tplName);
            if (tplBody == null) {
                return null;
            }

            boolean isWl = tplBody.matches("(?s).*\"isWhitelist\"\\s*:\\s*true.*");

            String arr = extractArray(tplBody, "appList");
            Result r = new Result();
            r.isWhitelist = isWl;
            r.source = path + " (模板: " + tplName + ")";

            if (arr != null) {
                int i = 0;
                while (true) {
                    String item = nthString(arr, i);
                    if (item == null) {
                        break;
                    }
                    if (item.length() > 0) {
                        if (isWl) {
                            r.visible.add(item);
                        } else {
                            r.hidden.add(item);
                        }
                    }
                    i++;
                }
            }

            XposedBridge.log(TAG + " 解析模板 " + tplName
                    + ": isWhitelist=" + isWl
                    + ", 条目=" + (isWl ? r.visible.size() : r.hidden.size()));
            return r;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " JSON 解析失败: " + t.getMessage());
            return null;
        }
    }

    private static String extractObject(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int brace = json.indexOf('{', k);
        if (brace < 0) {
            return null;
        }
        int end = matchBrace(json, brace, '{', '}');
        return end > brace ? json.substring(brace + 1, end) : null;
    }

    private static String extractArray(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int br = json.indexOf('[', k);
        if (br < 0) {
            return null;
        }
        int end = matchBrace(json, br, '[', ']');
        return end > br ? json.substring(br, end + 1) : null;
    }

    private static int matchBrace(String s, int start, char open, char close) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            if (c == '"') {
                inStr = !inStr;
                continue;
            }
            if (inStr) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String firstKey(String objBody) {
        int i = 0;
        while (i < objBody.length()) {
            char c = objBody.charAt(i);
            if (c == '"') {
                int end = objBody.indexOf('"', i + 1);
                if (end < 0) {
                    return null;
                }
                int colon = objBody.indexOf(':', end);
                if (colon < 0) {
                    return null;
                }
                String between = objBody.substring(end + 1, colon).trim();
                if (between.length() == 0) {
                    return objBody.substring(i + 1, end);
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return null;
    }

    private static String nthString(String arr, int n) {
        int idx = 0;
        int count = -1;
        while (idx < arr.length()) {
            int q = arr.indexOf('"', idx);
            if (q < 0) {
                return null;
            }
            int e = arr.indexOf('"', q + 1);
            if (e < 0) {
                return null;
            }
            count++;
            if (count == n) {
                return arr.substring(q + 1, e);
            }
            idx = e + 1;
        }
        return null;
    }
}
