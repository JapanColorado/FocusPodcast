package allen.town.focus_common.util;

import java.util.ArrayList;
import java.util.List;

public class Lists {
    public static <T> ArrayList<T> emptyList() {
        return new ArrayList<>();
    }

    public static <T> List<T> from(T... tArr) {
        ArrayList emptyList = emptyList();
        for (T t : tArr) {
            emptyList.add(t);
        }
        return emptyList;
    }
}
