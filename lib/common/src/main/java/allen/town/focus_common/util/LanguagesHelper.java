package allen.town.focus_common.util;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/* loaded from: classes5.dex */
public class LanguagesHelper {
    private static final ArrayList<String> SUPPORTED_LANGUAGES;
    private static final Map<String, String> SUPPORTED_LANGUAGES_DIALECT;

    static {
        ArrayList<String> arrayList = new ArrayList<>();
        SUPPORTED_LANGUAGES = arrayList;
        HashMap hashMap = new HashMap();
        SUPPORTED_LANGUAGES_DIALECT = hashMap;
        hashMap.put("ar", "");
        hashMap.put("de", "DE");
        hashMap.put("el", "GR");
        hashMap.put("en", "US");
        hashMap.put("es", "419");
        hashMap.put("fa", "");
        hashMap.put("fr", "FR");
        hashMap.put("hu", "HU");
        hashMap.put("id", "");
        hashMap.put("in", "");
        hashMap.put("it", "IT");
        hashMap.put("ja", "JP");
        hashMap.put("ko", "KR");
        hashMap.put("nl", "NL");
        hashMap.put("pl", "PL");
        hashMap.put("pt", "BR");
        hashMap.put("ro", "");
        hashMap.put("ru", "RU");
        hashMap.put("sv", "SE");
        hashMap.put("th", "");
        hashMap.put("tr", "TR");
        hashMap.put("uk", "");
        hashMap.put("vi", "");
        hashMap.put("zh", "CN");
        hashMap.put("ms", "");
        hashMap.put("fi", "FI");
        hashMap.put("nb", "NO");
        hashMap.put("no", "");
        hashMap.put("hi", "IN");
        hashMap.put("iw", "IL");
        hashMap.put("he", "");
        hashMap.put("da", "DK");
    }

    public static String getLanguageDialect(String str) {
        String str2 = SUPPORTED_LANGUAGES_DIALECT.get(str);
        return str2 == null ? "" : str2;
    }

    public static boolean isCurrentLanguageRtlAndSupported(Context context) {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == 1;
    }


    public static boolean isLanguageSupported(String str) {
        return SUPPORTED_LANGUAGES.contains(str);
    }
}
