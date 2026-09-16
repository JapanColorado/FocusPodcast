package allen.town.focus_common.util;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Timber {

    public static final List<Tree> FOREST = new CopyOnWriteArrayList();
    private static final Tree TREE_OF_SOULS = new Tree() {
        /* class timber.log.Timber.AnonymousClass1 */

        @Override // timber.log.Timber.Tree
        public void v(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).v(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void v(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).v(th, str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void d(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).d(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void d(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).d(th, str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void i(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).i(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void i(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).i(th, str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void w(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).w(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void w(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).w(th, str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void e(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).e(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void e(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).e(th, str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void wtf(String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).wtf(str, objArr);
            }
        }

        @Override // timber.log.Timber.Tree
        public void wtf(Throwable th, String str, Object... objArr) {
            List access$100 = Timber.FOREST;
            int size = access$100.size();
            for (int i = 0; i < size; i++) {
                ((Tree) access$100.get(i)).wtf(th, str, objArr);
            }
        }


        @Override // timber.log.Timber.Tree
        public void log(int i, String str, String str2, Throwable th) {
            throw new AssertionError("Missing override for log method.");
        }
    };

    public static void v(String str, Object... objArr) {
        TREE_OF_SOULS.v(str, objArr);
    }

    public static void v(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.v(th, str, objArr);
    }

    public static void d(String str, Object... objArr) {
        TREE_OF_SOULS.d(str, objArr);
    }

    public static void d(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.d(th, str, objArr);
    }

    public static void i(String str, Object... objArr) {
        TREE_OF_SOULS.i(str, objArr);
    }

    public static void i(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.i(th, str, objArr);
    }

    public static void w(String str, Object... objArr) {
        TREE_OF_SOULS.w(str, objArr);
    }

    public static void w(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.w(th, str, objArr);
    }

    public static void e(String str, Object... objArr) {
        TREE_OF_SOULS.e(str, objArr);
    }

    public static void e(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.e(th, str, objArr);
    }

    public static void wtf(String str, Object... objArr) {
        TREE_OF_SOULS.wtf(str, objArr);
    }

    public static void wtf(Throwable th, String str, Object... objArr) {
        TREE_OF_SOULS.wtf(th, str, objArr);
    }

    public static Tree asTree() {
        return TREE_OF_SOULS;
    }

    public static Tree tag(String str) {
        List<Tree> list = FOREST;
        int size = list.size();
        for (int i = 0; i < size; i++) {
            list.get(i).explicitTag.set(str);
        }
        return TREE_OF_SOULS;
    }

    public static void plant(Tree tree) {
        if (tree == null) {
            throw new NullPointerException("tree == null");
        } else if (tree != TREE_OF_SOULS) {
            FOREST.add(tree);
        } else {
            throw new IllegalArgumentException("Cannot plant Timber into itself.");
        }
    }

    public static void uproot(Tree tree) {
        if (!FOREST.remove(tree)) {
            throw new IllegalArgumentException("Cannot uproot tree which is not planted: " + tree);
        }
    }

    public static void uprootAll() {
        FOREST.clear();
    }

    private Timber() {
        throw new AssertionError("No instances.");
    }

    public static abstract class Tree {

        public final ThreadLocal<String> explicitTag = new ThreadLocal<>();


        protected abstract void log(int i, String str, String str2, Throwable th);

        /* access modifiers changed from: package-private */
        public String getTag() {
            String str = this.explicitTag.get();
            if (str != null) {
                this.explicitTag.remove();
            }
            return str;
        }

        public void v(String str, Object... objArr) {
            prepareLog(2, null, str, objArr);
        }

        public void v(Throwable th, String str, Object... objArr) {
            prepareLog(2, th, str, objArr);
        }

        public void d(String str, Object... objArr) {
            prepareLog(3, null, str, objArr);
        }

        public void d(Throwable th, String str, Object... objArr) {
            prepareLog(3, th, str, objArr);
        }

        public void i(String str, Object... objArr) {
            prepareLog(4, null, str, objArr);
        }

        public void i(Throwable th, String str, Object... objArr) {
            prepareLog(4, th, str, objArr);
        }

        public void w(String str, Object... objArr) {
            prepareLog(5, null, str, objArr);
        }

        public void w(Throwable th, String str, Object... objArr) {
            prepareLog(5, th, str, objArr);
        }

        public void e(String str, Object... objArr) {
            prepareLog(6, null, str, objArr);
        }

        public void e(Throwable th, String str, Object... objArr) {
            prepareLog(6, th, str, objArr);
        }

        public void wtf(String str, Object... objArr) {
            prepareLog(7, null, str, objArr);
        }

        public void wtf(Throwable th, String str, Object... objArr) {
            prepareLog(7, th, str, objArr);
        }

        private void prepareLog(int i, Throwable th, String str, Object... objArr) {
            if (str != null && str.length() == 0) {
                str = null;
            }
            if (str != null) {
                if (objArr != null && objArr.length > 0) {
                    str = String.format(str, objArr);
                }
                if (th != null) {
                    str = str + "\n" + Log.getStackTraceString(th);
                }
            } else if (th != null) {
                str = Log.getStackTraceString(th);
            } else {
                return;
            }
            log(i, getTag(), str, th);
        }
    }

    public static class DebugTree extends Tree {
        private static final Pattern ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$");
        private static final int CALL_STACK_INDEX = 5;
        private static final int MAX_LOG_LENGTH = 4000;


        public String createStackElementTag(StackTraceElement stackTraceElement) {
            String className = stackTraceElement.getClassName();
            Matcher matcher = ANONYMOUS_CLASS.matcher(className);
            if (matcher.find()) {
                className = matcher.replaceAll("");
            }
            return className.substring(className.lastIndexOf(46) + 1);
        }

        /* access modifiers changed from: package-private */
        @Override // timber.log.Timber.Tree
        public final String getTag() {
            String tag = super.getTag();
            if (tag != null) {
                return tag;
            }
            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            if (stackTrace.length > 5) {
                return createStackElementTag(stackTrace[5]);
            }
            throw new IllegalStateException("Synthetic stacktrace didn't have enough elements: are you using proguard?");
        }


        @Override // timber.log.Timber.Tree
        public void log(int i, String str, String str2, Throwable th) {
            int min;
            if (str2.length() >= MAX_LOG_LENGTH) {
                int i2 = 0;
                int length = str2.length();
                while (i2 < length) {
                    int indexOf = str2.indexOf(10, i2);
                    if (indexOf == -1) {
                        indexOf = length;
                    }
                    while (true) {
                        min = Math.min(indexOf, i2 + MAX_LOG_LENGTH);
                        String substring = str2.substring(i2, min);
                        if (i == 7) {
                            Log.wtf(str, substring);
                        } else {
                            Log.println(i, str, substring);
                        }
                        if (min >= indexOf) {
                            break;
                        }
                        i2 = min;
                    }
                    i2 = min + 1;
                }
            } else if (i == 7) {
                Log.wtf(str, str2);
            } else {
                Log.println(i, str, str2);
            }
        }
    }
}
