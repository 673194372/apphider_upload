package de.robv.android.xposed;

/** Xposed API stub —— 编译期用，运行时不打包 */
public final class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            return null;
        }

        public void setResult(Object r) {
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setThrowable(Throwable t) {
        }
    }

    public class Unhook {
        public void unhook() {
        }
    }
}
