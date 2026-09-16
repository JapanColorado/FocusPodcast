package allen.town.podcast.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import allen.town.focus_common.util.LanguagesHelper;
import allen.town.focus_common.util.Util;
import allen.town.podcast.R;
import allen.town.podcast.databinding.ViewTagsSectionBinding;
import allen.town.podcast.discovery.EnumItuneCategory;
import allen.town.podcast.discovery.ItunesCategoryTopLoader;
import allen.town.podcast.event.ItunesCategoryChangeEvent;
import allen.town.podcast.util.BalancedUIHelper;

public class TagsSectionView extends LinearLayout {
    private static final String TAG = "TagsSectionView";
    private ViewTagsSectionBinding binding;
    private List<EnumItuneCategory> mTags;

    public TagsSectionView(Context context) {
        super(context);
        inflateContent();
    }

    private void inflateTagsViewBalanced(List<EnumItuneCategory> list) {
        if (LanguagesHelper.isCurrentLanguageRtlAndSupported(getContext())) {
            this.binding.scrollView.postDelayed(new Runnable() { // from class: fm.player.ui.discover.TagsSectionView.1
                @Override // java.lang.Runnable
                public void run() {
                    TagsSectionView.this.binding.scrollView.fullScroll(66);
                }
            }, 10L);
        } else {
            this.binding.scrollView.scrollTo(0, 0);
        }
        this.binding.tagsContainer.removeAllViews();
        int dpToPx = Util.dp2Px(getContext(), 8);
        int dpToPx2 = Util.dp2Px(getContext(), -2);
        ArrayList <FrameLayout> arrayList = new ArrayList();
        int selectedIndex = 0;
        for (int index = 0; index < list.size(); index++) {
            if (categoryCode == list.get(index).getCode()) {
                selectedIndex = index;
                break;
            }
        }
        for (int index = 0; index < list.size(); index++) {
            FrameLayout frameLayout = new FrameLayout(getContext());

            View entryView = LayoutInflater.from(getContext()).inflate(R.layout.single_tag_chip, null, false);
            Chip chip = entryView.findViewById(R.id.chip);


            final int finalIndex = index;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    categoryCode = list.get(finalIndex).getCode();
                    int count = arrayList.size();
                    for (int i = 0; i < count; i++) {
                        if(arrayList.get(i).getChildAt(0) instanceof Chip){
                            ((Chip)arrayList.get(i).getChildAt(0)).setChecked(i == finalIndex);
                            ((Chip)arrayList.get(i).getChildAt(0)).setCheckedIconVisible(i == finalIndex);
                        }
                    }

                    EventBus.getDefault().post(new ItunesCategoryChangeEvent(categoryCode));
                }
            });

            if(selectedIndex == index){
//                binding.tagsContainer.post(chip::performClick);
                //without this, the chip does not appear selected after a theme switch on this screen (reason unknown)
                binding.tagsContainer.post(() -> {
                    chip.setChecked(true);
                    chip.setCheckedIconVisible(true);
                });
            }else {
                chip.setChecked(false);
                chip.setCheckedIconVisible(false);
            }


            chip.setText(list.get(index).getStrId());
            LayoutParams layoutParams = new LayoutParams(-2, -2);
            layoutParams.setMargins(0, dpToPx2, dpToPx, dpToPx2);
            layoutParams.setMarginEnd(layoutParams.rightMargin);
            frameLayout.setLayoutParams(layoutParams);
            frameLayout.addView(entryView);
            frameLayout.measure(0, 0);
            frameLayout.setTag(Integer.valueOf(list.get(index).hashCode()));
            arrayList.add(frameLayout);
        }

        ArrayList<ArrayList<FrameLayout>> balanceViewsDiscoveryCarousel = BalancedUIHelper.balanceViewsDiscoveryCarousel(arrayList, 3);
        int size = balanceViewsDiscoveryCarousel.size();
        Iterator<ArrayList<FrameLayout>> it2 = balanceViewsDiscoveryCarousel.iterator();
        int i = 0;
        while (it2.hasNext()) {
            ArrayList<FrameLayout> next = it2.next();
            i++;
            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setLayoutParams(new LayoutParams(-2, -2));
            int size2 = next.size();
            Iterator<FrameLayout> it3 = next.iterator();
            int i2 = 0;
            while (it3.hasNext()) {
                View next2 = it3.next();
                i2++;
                linearLayout.addView(next2);
                if (i == size || i2 == size2) {
                    LayoutParams layoutParams2 = (LayoutParams) next2.getLayoutParams();
                    if (i == size) {
                        layoutParams2.bottomMargin = 0;
                    }
                    if (i2 == size2) {
                        layoutParams2.rightMargin = 0;
                        layoutParams2.setMarginEnd(0);
                    }
                    next2.setLayoutParams(layoutParams2);
                }
            }
            this.binding.tagsContainer.addView(linearLayout);
        }
    }

    private void init() {
        if (LanguagesHelper.isCurrentLanguageRtlAndSupported(getContext())) {
            this.binding.scrollView.setLayoutDirection(LAYOUT_DIRECTION_LTR);
            this.binding.tagsContainer.setLayoutDirection(LAYOUT_DIRECTION_LTR);
            this.binding.tagsContainer.setGravity(5);
        }
    }

    private void inflateContent() {
        binding = ViewTagsSectionBinding.inflate(LayoutInflater.from(getContext()), this);
    }

    @Override // android.view.View
    public void onFinishInflate() {
        super.onFinishInflate();
        init();
    }

    private int categoryCode;

    public void setCurrentCode(int categoryCode) {
        this.categoryCode = categoryCode;
        setData();
    }

    public void setData() {
        this.mTags = ItunesCategoryTopLoader.enumItuneCategoryList;
        inflateTagsViewBalanced(mTags);
    }

    public TagsSectionView(Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);
        inflateContent();
    }

    public TagsSectionView(Context context, @Nullable AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        inflateContent();
    }
}
