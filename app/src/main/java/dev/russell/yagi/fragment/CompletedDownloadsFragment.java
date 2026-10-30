package dev.russell.yagi.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.faltenreich.skeletonlayout.Skeleton;
import com.faltenreich.skeletonlayout.SkeletonLayoutUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.russell.yagi.common.util.DoubleClickBackToContentTopListener;
import dev.russell.yagi.R;
import dev.russell.yagi.activity.MainActivity;
import dev.russell.yagi.adapter.EpisodeItemListAdapter;
import dev.russell.yagi.adapter.MultiSelectAdapter;
import dev.russell.yagi.core.event.DownloadEvent;
import dev.russell.yagi.core.event.DownloadLogEvent;
import dev.russell.yagi.core.pref.PlaybackPreferences;
import dev.russell.yagi.core.service.download.DownloadService;
import dev.russell.yagi.core.storage.DBReader;
import dev.russell.yagi.core.storage.DBWriter;
import dev.russell.yagi.core.storage.PlayedDownloadSelector;
import dev.russell.yagi.core.util.FeedItemUtil;
import dev.russell.yagi.core.util.download.AutoUpdateManager;
import dev.russell.yagi.core.util.menuhandler.MenuItemUtils;
import dev.russell.yagi.event.FeedItemEvent;
import dev.russell.yagi.event.PlayerStatusEvent;
import dev.russell.yagi.event.UnreadItemsUpdateEvent;
import dev.russell.yagi.event.playback.PlaybackPositionEvent;
import dev.russell.yagi.fragment.actions.EpisodeMultiSelectActionHandler;
import dev.russell.yagi.menuprocess.FeedItemMenuProcess;
import dev.russell.yagi.model.feed.FeedItem;
import dev.russell.yagi.model.feed.FeedMedia;
import dev.russell.yagi.common.util.TopSnackbarUtil;
import dev.russell.yagi.common.views.AccentMaterialDialog;
import dev.russell.yagi.ui.common.PagedToolbarFragment;
import dev.russell.yagi.util.SkeletonRecyclerDelay;
import dev.russell.yagi.view.EmptyViewHandler;
import dev.russell.yagi.viewholder.EpisodeItemViewHolder;
import dev.russell.yagi.theme.util.scroll.ThemedFastScroller;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Displays all completed downloads and provides a button to delete them.
 */
public class CompletedDownloadsFragment extends Fragment implements DoubleClickBackToContentTopListener.IBackToContentTopView,
        EpisodeItemListAdapter.OnSelectModeListener {

    private static final String TAG = CompletedDownloadsFragment.class.getSimpleName();

    private List<FeedItem> items = new ArrayList<>();
    private CompletedDownloadsListAdapter adapter;
    private RecyclerView recyclerView;
    private Disposable disposable;
    private EmptyViewHandler emptyView;

    private boolean isUpdatingFeeds = false;
    private Skeleton skeleton;
    private SkeletonRecyclerDelay skeletonRecyclerDelay;

    private View summaryHeader;
    private TextView summaryText;
    private View deletePlayedButton;
    private Disposable summaryDisposable;
    private Disposable deletePlayedDisposable;
    /** What the header shows; null until the first load finished. */
    @Nullable
    private DownloadsSummary summary;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.completed_downloads_fragment, container, false);

        summaryHeader = root.findViewById(R.id.downloadsSummaryHeader);
        summaryText = root.findViewById(R.id.downloadsSummaryText);
        deletePlayedButton = root.findViewById(R.id.deletePlayedButton);
        deletePlayedButton.setOnClickListener(v -> confirmDeletePlayed());

        recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
        adapter = new CompletedDownloadsListAdapter((MainActivity) getActivity());
        adapter.setOnSelectModeListener(this);
        recyclerView.setAdapter(adapter);
        ThemedFastScroller.create(recyclerView);

        adapter.setOnMenuItemClickListener(new MultiSelectAdapter.OnMenuItemClickListener() {
            @Override
            public void onMenuItemClick(MenuItem item) {
                new EpisodeMultiSelectActionHandler(((MainActivity) getActivity()), adapter.getSelectedItems())
                        .handleAction(item.getItemId());
                adapter.endSelectMode();
            }
        });


        addEmptyView();
        skeleton = SkeletonLayoutUtils.applySkeleton(recyclerView, R.layout.item_small_recyclerview_skeleton,15);
        skeletonRecyclerDelay = new SkeletonRecyclerDelay(skeleton,recyclerView);
        skeletonRecyclerDelay.showSkeleton();
        EventBus.getDefault().register(this);
        return root;
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        if (summaryDisposable != null) {
            summaryDisposable.dispose();
        }
        if (deletePlayedDisposable != null) {
            deletePlayedDisposable.dispose();
        }
        adapter.endSelectMode();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(false);
        isUpdatingFeeds = MenuItemUtils.updateRefreshMenuItem(menu, R.id.refresh_item, updateRefreshMenuItemChecker);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_item) {
            AutoUpdateManager.runImmediate(requireContext());
            return true;
        }
        return false;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DownloadEvent event) {
        if (event.hasChangedFeedUpdateStatus(isUpdatingFeeds)) {
            ((PagedToolbarFragment) getParentFragment()).invalidateOptionsMenuIfActive(this);
        }
    }

    private final MenuItemUtils.UpdateRefreshMenuItemChecker updateRefreshMenuItemChecker =
            () -> DownloadService.isRunning() && DownloadService.isDownloadingFeeds();

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        FeedItem selectedItem = adapter.getLongPressedItem();
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection");
            return super.onContextItemSelected(item);
        }
        if (adapter.onContextItemSelected(item)) {
            return true;
        }

        return FeedItemMenuProcess.onMenuItemClicked(this, item.getItemId(), selectedItem);
    }

    private void addEmptyView() {
        emptyView = new EmptyViewHandler(getActivity());
        emptyView.setIcon(R.drawable.ic_download);
        emptyView.setTitle(R.string.no_comp_downloads_head_label);
        emptyView.attachToRecyclerView(recyclerView);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedItemEvent event) {
        if (items == null) {
            return;
        } else if (adapter == null) {
            loadItems();
            return;
        }
        boolean changed = false;
        for (int i = 0, size = event.items.size(); i < size; i++) {
            FeedItem item = event.items.get(i);
            int pos = FeedItemUtil.indexOfItemWithId(items, item.getId());
            if (pos >= 0) {
                changed = true;
                items.remove(pos);
                if (item.getMedia().isDownloaded()) {
                    items.add(pos, item);
                    adapter.notifyItemChangedCompat(pos);
                } else {
                    adapter.notifyItemRemoved(pos);
                }
            }
        }
        if (changed) {
            refreshSummary();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaybackPositionEvent event) {
        if (adapter != null) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                EpisodeItemViewHolder holder = (EpisodeItemViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
                if (holder != null && holder.isCurrentlyPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event);
                    break;
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadLogChanged(DownloadLogEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        loadItems();
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        emptyView.hide();
        // The missing-file sweep runs once per process in MainActivity (and after a storage
        // folder change); loadItems() fires on every player status event, far too often for it.
        disposable = Observable.fromCallable(() -> {
                    List<FeedItem> result = DBReader.getDownloadedItems();
                    return new LoadResult(result, DownloadsSummary.of(result));
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> {
                    List<FeedItem> result = loaded.items;
                    items = result;
                    showSummary(loaded.summary);
                    adapter.updateItems(result);
                    ((PagedToolbarFragment) getParentFragment()).invalidateOptionsMenuIfActive(this);
                    if(skeleton.isSkeleton()) {
                        skeletonRecyclerDelay.showOriginal();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    /** Recomputes the header from the list as it is now, after rows changed in place. */
    private void refreshSummary() {
        if (summaryDisposable != null) {
            summaryDisposable.dispose();
        }
        List<FeedItem> snapshot = new ArrayList<>(items);
        summaryDisposable = Single.fromCallable(() -> DownloadsSummary.of(snapshot))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showSummary, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void showSummary(@NonNull DownloadsSummary newSummary) {
        summary = newSummary;
        if (newSummary.count == 0) {
            summaryHeader.setVisibility(View.GONE);
            return;
        }
        summaryHeader.setVisibility(View.VISIBLE);
        summaryText.setText(getResources().getQuantityString(R.plurals.num_episodes,
                newSummary.count, newSummary.count)
                + " · " + Formatter.formatShortFileSize(requireContext(), newSummary.bytes));
        deletePlayedButton.setVisibility(newSummary.playedCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void confirmDeletePlayed() {
        DownloadsSummary current = summary;
        if (current == null || current.playedCount == 0) {
            return;
        }
        new AccentMaterialDialog(requireContext(), R.style.MaterialAlertDialogTheme)
                .setTitle(getResources().getQuantityString(R.plurals.delete_played_confirm,
                        current.playedCount, current.playedCount,
                        Formatter.formatShortFileSize(requireContext(), current.playedBytes)))
                .setMessage(R.string.delete_played_confirm_msg)
                .setPositiveButton(R.string.delete_label, (dialog, which) -> deletePlayed())
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void deletePlayed() {
        if (deletePlayedDisposable != null) {
            deletePlayedDisposable.dispose();
        }
        Context appContext = requireContext().getApplicationContext();
        Activity activity = requireActivity();
        deletePlayedDisposable = Single.fromCallable(() -> DBWriter.deletePlayedDownloads(appContext).get())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> {
                    TopSnackbarUtil.showSnack(activity, activity.getResources().getQuantityString(
                            R.plurals.deleted_multi_episode_batch_label, deleted, deleted),
                            Toast.LENGTH_SHORT);
                    loadItems();
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    /** The list together with the header computed from it on the same background thread. */
    private static final class LoadResult {
        final List<FeedItem> items;
        final DownloadsSummary summary;

        LoadResult(List<FeedItem> items, DownloadsSummary summary) {
            this.items = items;
            this.summary = summary;
        }
    }

    /**
     * The numbers in the header: all downloads, and the played ones "Delete played" would remove.
     * Sizes come from the database; when a size was never recorded the file itself is measured,
     * so this must run off the main thread. Local folder files are not the app's storage and are
     * not counted in the size.
     */
    private static final class DownloadsSummary {
        final int count;
        final long bytes;
        final int playedCount;
        final long playedBytes;

        private DownloadsSummary(int count, long bytes, int playedCount, long playedBytes) {
            this.count = count;
            this.bytes = bytes;
            this.playedCount = playedCount;
            this.playedBytes = playedBytes;
        }

        static DownloadsSummary of(List<FeedItem> items) {
            long bytes = 0;
            for (FeedItem item : items) {
                bytes += sizeOf(item.getMedia());
            }
            List<FeedItem> played = PlayedDownloadSelector.select(items,
                    PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            long playedBytes = 0;
            for (FeedItem item : played) {
                playedBytes += sizeOf(item.getMedia());
            }
            return new DownloadsSummary(items.size(), bytes, played.size(), playedBytes);
        }

        private static long sizeOf(@Nullable FeedMedia media) {
            if (media == null || media.isContentUri()) {
                return 0;
            }
            if (media.getSize() > 0) {
                return media.getSize();
            }
            String path = media.getFile_url();
            return path != null ? new File(path).length() : 0;
        }
    }

    @Override
    public void onStartSelectMode() {
        ((PagedToolbarFragment) getParentFragment()).getToolbar().setVisibility(View.GONE);
    }

    @Override
    public void onEndSelectMode() {
        ((PagedToolbarFragment) getParentFragment()).getToolbar().setVisibility(View.VISIBLE);
    }

    @Override
    public void backToContentTop() {
        recyclerView.scrollToPosition(5);
        recyclerView.post(() -> recyclerView.smoothScrollToPosition(0));
    }

    private class CompletedDownloadsListAdapter extends EpisodeItemListAdapter {

        public CompletedDownloadsListAdapter(MainActivity mainActivity) {
            super(mainActivity,R.menu.downloads_action_menus);
        }

        @Override
        public void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
            if (!inActionMode()) {
                holder.downloadedButton.setVisibility(View.GONE);
//                DeleteActionButton actionButton = new DeleteActionButton(getItem(pos));
//                actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, getActivity());
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            if (!inActionMode()) {
                menu.findItem(R.id.multi_select).setVisible(true);
            }
            MenuItemUtils.setOnClickListeners(menu, CompletedDownloadsFragment.this::onContextItemSelected);
        }
    }
}
