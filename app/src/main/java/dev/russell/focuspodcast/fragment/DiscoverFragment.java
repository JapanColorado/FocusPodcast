package dev.russell.focuspodcast.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.faltenreich.skeletonlayout.Skeleton;
import com.faltenreich.skeletonlayout.SkeletonLayoutUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import dev.russell.focuspodcast.common.util.DoubleClickBackToContentTopListener;
import dev.russell.focuspodcast.common.util.Timber;
import dev.russell.focuspodcast.MyApp;
import dev.russell.focuspodcast.R;
import dev.russell.focuspodcast.activity.MainActivity;
import dev.russell.focuspodcast.adapter.ItunesAdapter;
import dev.russell.focuspodcast.core.storage.DBReader;
import dev.russell.focuspodcast.databinding.DiscoverLayoutBinding;
import dev.russell.focuspodcast.discovery.CombinedSearcher;
import dev.russell.focuspodcast.discovery.ItunesCategoryTopLoader;
import dev.russell.focuspodcast.discovery.PodcastSearchResult;
import dev.russell.focuspodcast.event.FeedListUpdateEvent;
import dev.russell.focuspodcast.event.ItunesCategoryChangeEvent;
import dev.russell.focuspodcast.common.common.prefs.supportv7.dialogs.PreferenceListDialog;
import dev.russell.focuspodcast.util.SkeletonRecyclerDelay;
import dev.russell.focuspodcast.view.TagsSectionView;
import dev.russell.focuspodcast.theme.util.scroll.ThemedFastScroller;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Provides actions for adding new podcast subscriptions.
 */
public class DiscoverFragment extends Fragment implements Toolbar.OnMenuItemClickListener , DoubleClickBackToContentTopListener.IBackToContentTopView{

    public static final String TAG = "DiscoverFragment";
    private static final String KEY_UP_ARROW = "up_arrow";

    private DiscoverLayoutBinding viewBinding;
    private MainActivity activity;
    private boolean displayUpArrow;

    private SharedPreferences prefs;

    /**
     * Adapter responsible with the search results.
     */
    private ItunesAdapter adapter;
    private RecyclerView gridView;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;

    /**
     * List of podcasts retreived from the search.
     */
    private List<PodcastSearchResult> topList;
    private Disposable disposable;
    private String countryCode = "US";
    private int categoryCode;
    private Skeleton skeleton;
    private SkeletonRecyclerDelay skeletonRecyclerDelay;

    /**
     * Replace adapter data with provided search results from SearchTask.
     *
     * @param result List of Podcast objects containing search results
     */
    private void updateData(List<PodcastSearchResult> result) {
        if (result != null && result.size() > 0) {
            gridView.setVisibility(View.VISIBLE);
            txtvEmpty.setVisibility(View.GONE);
        } else {
            gridView.setVisibility(View.GONE);
            txtvEmpty.setVisibility(View.VISIBLE);
        }
        adapter.addAll(result);
    }



    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        viewBinding = DiscoverLayoutBinding.inflate(getLayoutInflater());
        activity = (MainActivity) getActivity();

        Toolbar toolbar = viewBinding.appBarLayout.getToolbar();
        toolbar.setOnMenuItemClickListener(this);
        toolbar.setTitle(R.string.discover);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.discover);

        View root = viewBinding.getRoot();
        gridView = root.findViewById(R.id.gridView);
        gridView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItunesAdapter(getActivity(), new ArrayList<>(), true, false);
        gridView.setAdapter(adapter);
        toolbar.setOnClickListener(new DoubleClickBackToContentTopListener(this));
        skeleton = SkeletonLayoutUtils.applySkeleton(gridView, R.layout.item_small_recyclerview_skeleton, 15);
        skeletonRecyclerDelay = new SkeletonRecyclerDelay(skeleton, gridView);
        skeletonRecyclerDelay.showSkeleton();

        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);

        TagsSectionView tagsSectionView = root.findViewById(R.id.tags_view);
        tagsSectionView.setCurrentCode(categoryCode);

//        NestedScrollView nestedScrollView = root.findViewById(R.id.nested_scroll);
        ThemedFastScroller.create(gridView);
        //setting this to false breaks scrolling
//        nestedScrollView.setNestedScrollingEnabled(false);

        getSubedFeedsList();

        loadToplist(countryCode);
        return viewBinding.getRoot();
    }

    private Disposable getFeedsListDisposable;

    private void getSubedFeedsList() {
        getFeedsListDisposable = Observable.fromCallable(DBReader::getFeedList)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        feeds -> {
                            adapter.setSubscribedFeeds(feeds);
                            //download finished, the feed is now in the database
                        }, error -> Log.e(TAG, Log.getStackTraceString(error))
                );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.switch_country) {
            showSwitchCountryDialog();
            return true;
        } else if (itemId == R.id.search) {
            performSearch();
            return true;
        }
        return false;
    }


    private void performSearch() {
        String query = "";

        activity.loadChildFragment(OnlineSearchFragment.newInstance(CombinedSearcher.class, query));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //keeping this makes adding files from OPML fail after switching to dark mode; unclear whether commenting it out causes other problems, the git history says little about it
//        setRetainInstance(true);
        prefs = getActivity().getSharedPreferences(ItunesCategoryTopLoader.PREFS, MODE_PRIVATE);
        countryCode = prefs.getString(ItunesCategoryTopLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().getCountry());
        categoryCode = prefs.getInt(ItunesCategoryTopLoader.PREF_KEY_CATEGORY_CODE, 0);
    }

    private void loadToplist(String country) {
        if (disposable != null) {
            disposable.dispose();
        }

        skeleton.showSkeleton();
        //make it visible first: at this point only the skeleton is shown, not the real content
        gridView.setVisibility(View.VISIBLE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);

        ItunesCategoryTopLoader loader = new ItunesCategoryTopLoader(getContext());
        disposable = loader.loadToplist(country, 100, categoryCode).subscribe(
                podcasts -> {
                    skeletonRecyclerDelay.showOriginal();
                    topList = podcasts;
                    updateData(topList);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    skeletonRecyclerDelay.showOriginal();
                    if (error instanceof SocketTimeoutException
                            || error instanceof UnknownHostException) {
                        txtvError.setText(R.string.download_error_connection_error);
                    } else {
                        txtvError.setText(error.getMessage());
                    }
                    gridView.setVisibility(View.GONE);
                    txtvError.setVisibility(View.VISIBLE);
                    butRetry.setOnClickListener(v -> loadToplist(country));
                    butRetry.setVisibility(View.VISIBLE);
                });
    }

    public void showSwitchCountryDialog() {

        List<String> countryCodeArray = new ArrayList<String>(Arrays.asList(Locale.getISOCountries()));
        HashMap<String, String> countryCodeNames = new HashMap<String, String>();
        for (String code : countryCodeArray) {
            Locale locale = new Locale("", code);
            String countryName = locale.getDisplayCountry();
            if (countryName != null) {
                countryCodeNames.put(code, countryName);
            }
        }

        List<String> countryNamesSort = new ArrayList<String>(countryCodeNames.values());
        Collections.sort(countryNamesSort);

        final String[] countryList = (String[]) countryNamesSort.toArray(new String[]{});

        PreferenceListDialog preferenceListDialog = new PreferenceListDialog(getContext(),
                getString(R.string.pref_language_name));

        preferenceListDialog.setSelection(countryNamesSort.indexOf(countryCodeNames.get(countryCode)));

        preferenceListDialog.openDialog(countryList);
        preferenceListDialog.setOnPreferenceChangedListener(which -> {
            String countryName = countryList[which];

            for (Object o : countryCodeNames.keySet()) {
                if (countryCodeNames.get(o).equals(countryName)) {
                    countryCode = o.toString();
                    break;
                }
            }

            prefs.edit()
                    .putString(ItunesCategoryTopLoader.PREF_KEY_COUNTRY_CODE, countryCode)
                    .apply();

            loadToplist(countryCode);

        });
    }

    @Override
    public void backToContentTop() {
        gridView.scrollToPosition(5);
        gridView.post(() -> gridView.smoothScrollToPosition(0));
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }

        if (getFeedsListDisposable != null) {
            getFeedsListDisposable.dispose();
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void changeItunesCategory(ItunesCategoryChangeEvent itunesCategoryChangeEvent) {
        categoryCode = itunesCategoryChangeEvent.getCode();
        prefs.edit().putInt(ItunesCategoryTopLoader.PREF_KEY_CATEGORY_CODE, categoryCode).apply();
        loadToplist(countryCode);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        //subscription state changed
        getSubedFeedsList();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
