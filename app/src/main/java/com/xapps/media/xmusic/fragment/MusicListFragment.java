package com.xapps.media.xmusic.fragment;

import android.annotation.SuppressLint;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.annotation.NonNull;
import androidx.core.content.*;
import androidx.core.view.ViewKt;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialSplitButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.transition.MaterialSharedAxis;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.databinding.*;
import com.xapps.media.xmusic.databinding.ActivityMainBinding;
import com.xapps.media.xmusic.helper.FabPlacementHelper;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.helper.SongSorter;
import com.xapps.media.xmusic.models.BottomSheetBehavior;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.service.PlayerService;
import com.xapps.media.xmusic.utils.*;
import com.xapps.media.xmusic.widget.ExpressiveSliderLayout;
import com.xapps.media.xmusic.widget.VuMeterView;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kotlin.Unit;
import me.zhanghai.android.fastscroll.FastScroller;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import me.zhanghai.android.fastscroll.PopupTextProvider;

public class MusicListFragment extends BaseFragment {
	
	public MusicListFragmentBinding binding;
    private int currentSongID, oldSongID = -1;
    private int oldPos = -1;
    private int currentPos= -1;
	public final Fragment f = this;
	private String Title = "";
	private String Artitst = "";
	private Uri coverUri;
	public int imageSize, size, statusBarHeight;
	private String path = "";
	private ActivityMainBinding activity;
    private MainActivity a;
    private SongsListAdapter songsAdapter;
    private boolean isPlaying = false;
    private ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private Handler mainHandler;
    private Drawable placeholder;
    private long lastClickTime = 0;
    private final long DEBOUNCE_MS = 300;
    private ConcatAdapter concatAdapter;
    
    private boolean forceUpdate = false;
    
    private FastScroller scroller;
    
    public static ExtendedFloatingActionButton fab;
    
    private int lastSpacing;
    private int defaultFabMargin;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
        a = (MainActivity) getActivity();
		binding = MusicListFragmentBinding.inflate(_inflater, _container, false);
		binding.shuffleButton.hide();
        mainHandler = new Handler(Looper.getMainLooper());
        if (getActivity() != null) binding.collapsingToolbar.setPadding(0, XUtils.getStatusBarHeight(getActivity()), 0, 0);
		initializeLogic();
        setUpListeners(); 
		return binding.getRoot();
	}
	
	private void initializeLogic() {
        loadSongs();
        fab = binding.shuffleButton;
        statusBarHeight = XUtils.getStatusBarHeight(getActivity());
        a = (MainActivity) getActivity();
        activity = a.getBinding();
        placeholder = ContextCompat.getDrawable(getActivity(), R.drawable.placeholder_small);
        imageSize = XUtils.convertToPx(getActivity(), 45f);
        ViewKt.doOnLayout(activity.bottomNavigation, v -> {
            if (getActivity() == null) return Unit.INSTANCE;
            a.setMusicListFragmentInstance(this);
            lastSpacing = XUtils.convertToPx(getActivity(), 5f) + activity.coversPager.getHeight()*2 + activity.bottomNavigation.getHeight()*2 ;
            binding.songsList.addItemDecoration(new BottomSpacingDecoration(lastSpacing));
            binding.songsList.setLayoutManager(new LinearLayoutManager(getContext()));
            /*FabPlacementHelper helper = new FabPlacementHelper(binding.shuffleButton, activity.expressiveBottomSheet, activity.bottomNavigation, binding.songsList);
			helper.wireUp(getViewLifecycleOwner());*/
			return Unit.INSTANCE;
        });
        
        binding.songsList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        binding.shuffleButton.setOnClickListener(v -> {
            shuffle();
        });
	}
    
    @Override
    public void onResume() {
        super.onResume();
    }
        
    @Override
    public void onPause() {
        super.onPause();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        fab = null;
    }
    
    public void setupSongsList(ArrayList<HashMap<String, Object>> list) {
        
    }
    
    public void setUpListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
			if (scroller != null) scroller.setForceHidden(true);
            forceUpdate = true;
            SongMetadataHelper.clearCachedList();
            loadSongs();
            a.loadSongs();
        });
    }
	
	public class SongsListAdapter extends RecyclerView.Adapter<SongsListAdapter.ViewHolder> implements PopupTextProvider {
		
        int c1 = MaterialColorUtils.colorPrimary;
        int c2 = MaterialColorUtils.colorSecondary;
        int c3 = MaterialColorUtils.colorOnSurface;
        int c4 = MaterialColorUtils.colorOutline;
        
        private ArrayList<Song> data = new ArrayList<>();
        
        private int spacing;
        private int resId = R.drawable.placeholder_small;
        private Uri uri;
        private String placeholderUri;
        
        private static final int TYPE_SINGLE = -1;
        private static final int TYPE_TOP = 0;
        private static final int TYPE_MIDDLE = 1;
        private static final int TYPE_BOTTOM = 2;
        
        private SongItemMiddleBinding binding;
        
		public SongsListAdapter(Context c, ArrayList<Song> arraylist) {
            spacing = XUtils.convertToPx(c, 5f);
            uri = Uri.parse("android.resource://" + c.getPackageName() + "/" + resId);
            placeholderUri = uri.toString();
            data = arraylist;
        }
		
		@Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layout;
            if (viewType == TYPE_TOP) layout = R.layout.song_item_top;
            else if (viewType == TYPE_BOTTOM) layout = R.layout.song_item_bottom;
            else if (viewType == TYPE_SINGLE) layout = R.layout.song_item_single;
            else layout = R.layout.song_item_middle;

            return new ViewHolder(inflater.inflate(layout, parent, false));
        }
        

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
		    View view = holder.itemView;
            binding = SongItemMiddleBinding.bind(view);  
            if (a.getController() != null && !a.getController().isPlaying()) binding.vumeterView.pause(); else binding.vumeterView.resume();
            if (getItemId(position) == currentSongID) {  
                binding.item.setChecked(true);  
                binding.vumeterFrame.setVisibility(View.VISIBLE);  
                binding.SongTitle.setTextColor(c1);  
                binding.SongArtist.setTextColor(c2);  
                if (a.getController() != null && a.getController().isPlaying()) binding.vumeterView.resume(); else binding.vumeterView.stop();  
            } else {  
                binding.item.setChecked(false);  
                binding.vumeterFrame.setVisibility(View.INVISIBLE);  
                binding.SongTitle.setTextColor(c3);  
                binding.SongArtist.setTextColor(c4);  
            }
		    coverUri = data.get(position).getArtworkUri();
		    Title = data.get(position).title;
		    Artitst = data.get(position).artist;
		    Glide.with(f)
		    .load(coverUri)
		    .centerCrop()  
            .fallback(placeholder)  
			.error(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  
            .transition(DrawableTransitionOptions.withCrossFade())  
            .placeholder(placeholder)  
            .override(imageSize, imageSize)
		    .into(binding.songCover);
		    if (Title == null || Title.equals("")) {
			    binding.SongTitle.setText(getString(R.string.unknown));
		    } else {
			    binding.SongTitle.setText(Title);
			    binding.SongArtist.setText(Artitst);
		    }

		    binding.item.setOnClickListener(v -> {  
                if (a.getController() == null ) return;  
                if (activity.expressiveBottomSheet.getState() == ExpressiveSliderLayout.STATE_SETTLING || activity.expressiveBottomSheet.getState() == ExpressiveSliderLayout.STATE_DRAGGING) return;  
                long currentTime = System.currentTimeMillis();  
                if (currentTime - lastClickTime < DEBOUNCE_MS) {  
                    return;  
                }  
                lastClickTime = currentTime;  
                a.setSong(position, forceUpdate);  
                if (forceUpdate) forceUpdate = false;  
                updateActiveItem(position);  
            });  
            binding.optionsIcon.setOnClickListener(v -> {  
                freeze(true);
                a.getBinding().songDetailsSheetContainer.songDetailsSheetRoot.showSheet(data.get(position));
            });

	    }
        
        @Override
        public int getItemCount() {
            return data.size();
        }
        
        @Override
        public long getItemId(int position) {
            String path = data.get(position).path;
            return path.hashCode();
        }
        
        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            super.onViewRecycled(holder);
			View v = holder.itemView.findViewById(R.id.songCover);
            Glide.with(holder.itemView.getContext()).clear(v); 
        }

        @Override
        public int getItemViewType(int position) {
            int size = getItemCount();

            if (size == 1) {
                return TYPE_SINGLE;
            }

            if (size == 2) {
                return position == 0 ? TYPE_TOP : TYPE_BOTTOM;
            }

            if (position == 0) return TYPE_TOP;
            if (position == size - 1) return TYPE_BOTTOM;
            return TYPE_MIDDLE;
        }
		
		@Override
        @Nullable
        public CharSequence getPopupText(View view, int position) {
            Song song = data.get(position);
            String title = song.title;

            if (title == null || title.isEmpty()) {
                return "#";
            }

            return String.valueOf(Character.toUpperCase(title.charAt(0)));
        }
		
		static class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
        
        @SuppressLint("NotifyDataSetChanged")
        public void updateData(ArrayList<Song> a) {
            data = a;
            notifyDataSetChanged();
        }
	}

    public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder>{
	    @Override
	    public HeaderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		    View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.header_view, parent, false);
		    return new HeaderViewHolder(headerView);
	    }
		
	    @Override
		public void onBindViewHolder(HeaderViewHolder holder, int position) {
            activity.bottomNavigation.post(() -> {
                if (scroller == null) scroller = new FastScrollerBuilder(binding.songsList).useMd2Style().setPopupStyle(t -> {
					t.setTextColor(MaterialColorUtils.colorOnPrimaryContainer);
					t.setTextSize(XUtils.convertSpToPx(getActivity(), 18f));
					t.setGravity(Gravity.CENTER);
					t.setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.shape_clover));
				}).setPopupTextProvider((view, position2) -> {
                if (position2 <= 0 || position2 - 1 >= RuntimeData.songs.size()) {
                    return null;
                }

                Song song = RuntimeData.songs.get(position2 - 1);
                String title = song.title;

                if (title == null || title.isEmpty()) {
                    return "#";
                }

                return String.valueOf(Character.toUpperCase(title.charAt(0)));
            }).setPadding(0, XUtils.convertToPx(getActivity(), 18f) + holder.itemView.getHeight(), 0, XUtils.convertToPx(getActivity(), 5f) + activity.coversPager.getHeight() + activity.bottomNavigation.getHeight()*2).build();
            });
			View view = holder.itemView;
			TextView sg = (TextView) view.findViewById(R.id.songs_count);
			sg.setText(getString(R.string.songs_count_format, size));
            MaterialButton orderButton = (MaterialButton) view.findViewById(R.id.order_type_button);
            orderButton.setChecked(!DataManager.isDescendingOrder());
            MaterialButton filterButton = (MaterialButton) view.findViewById(R.id.sort_filter_button);
            MaterialSplitButton msb = (MaterialSplitButton) view.findViewById(R.id.split_button);
            orderButton.setOnClickListener(v -> {
                boolean b = orderButton.isChecked();
                DataManager.setDescendingOrder(!b);
                SongSorter.sort(RuntimeData.songs, DataManager.getSongFilterType(), !b, sorted -> {
                    RuntimeData.songs = sorted;
                    songsAdapter.updateData(sorted);
                    a.updateSongsQueue(RuntimeData.songs);
                });
            });
            filterButton.setOnClickListener(v -> {
                LayoutFiltersContainerBinding b = LayoutFiltersContainerBinding.inflate(getLayoutInflater());
                BottomSheetDialog bs = new BottomSheetDialog(requireContext());
                bs.setContentView(b.getRoot());
                switch (DataManager.getSongFilterType()) {
                    case TITLE :
                        b.firstItem.setChecked(true);
                        b.firstRadio.setChecked(true);
                    break;
                    case ARTIST :
                        b.secondItem.setChecked(true);
                        b.secondRadio.setChecked(true);
                    break;
                    case ALBUM :
                        b.thirdItem.setChecked(true);
                        b.thirdRadio.setChecked(true);
                    break;
                    case ALBUM_ARTIST :
                        b.fourthItem.setChecked(true);
                        b.fourthRadio.setChecked(true);
                    break;
                    case YEAR :
                        b.fifthItem.setChecked(true);
                        b.fifthRadio.setChecked(true);
                    break;
                    case TRACK :
                        b.sixthItem.setChecked(true);
                        b.sixthRadio.setChecked(true);
                    break;
                    case DURATION :
                        b.seventhItem.setChecked(true);
                        b.seventhRadio.setChecked(true);
                    break;
                    case DATE_ADDED :
                        b.eighthItem.setChecked(true);
                        b.eighthRadio.setChecked(true);
                    break;
                    case DATE_MODIFIED :
                        b.ninethItem.setChecked(true);
                        b.ninethRadio.setChecked(true);
                    break;
                    case SIZE :
                        b.tenthItem.setChecked(true);
                        b.tenthRadio.setChecked(true);
                    break;
                }
                b.firstItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.TITLE);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.TITLE, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.secondItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ARTIST);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.ARTIST, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.thirdItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ALBUM);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.ALBUM, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.fourthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ALBUM_ARTIST);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.ALBUM_ARTIST, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.fifthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.YEAR);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.YEAR, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.sixthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.TRACK);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.TRACK, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.seventhItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DURATION);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.DURATION, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.eighthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DATE_ADDED);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.DATE_ADDED, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.ninethItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DATE_MODIFIED);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.DATE_MODIFIED, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                b.tenthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.SIZE);
                    SongSorter.sort(RuntimeData.songs, SongSorter.SortBy.SIZE, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songs = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songs);
                    });
                    bs.dismiss();
                });
                bs.setTitle(getString(R.string.sort_by));
                bs.show();
                if (DataManager.isBlurOn() && XUtils.areBlursOrDynamicColorsSupported()) XUtils.animateBlur(activity.Coordinator, true, 300);
                bs.setOnDismissListener(dialog -> {
                    if (DataManager.isBlurOn() && XUtils.areBlursOrDynamicColorsSupported()) XUtils.animateBlur(activity.Coordinator, false, 50);
                });
            });
            
		}
		
		@Override
		public int getItemCount() {
		    return 1;
		}
		public static class HeaderViewHolder extends RecyclerView.ViewHolder {
			public HeaderViewHolder(View itemView) {
				super(itemView);
			}
		}
	}

    public void adjustUI() {
        if (songsAdapter != null) {
            updateActiveItem(PlayerService.currentPosition);
        }
    }

    public class BottomSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int bottomSpacing;
        private int spacing;
        private int sideSpacing;
        public BottomSpacingDecoration(int bottomSpacing) {
            sideSpacing = XUtils.convertToPx(getActivity(), 12f);
            this.bottomSpacing = bottomSpacing;
            spacing = XUtils.convertToPx(getActivity(), 2f);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return;
            if (position == state.getItemCount() -1 ) {
                outRect.set(sideSpacing, 0, sideSpacing, lastSpacing);
            } else {
                outRect.set(sideSpacing, 0, sideSpacing, spacing);
            }
        }
    }

    public void shuffle() {
        Uri uri = Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + R.drawable.placeholder);
        String placeholderUri = uri.toString();
        int r = new Random().nextInt((RuntimeData.songs.size()-1 - 0) + 1) + 0;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < DEBOUNCE_MS) {
            return;
        }
        lastClickTime = currentTime;
        a.setSong(r, forceUpdate);
        if (forceUpdate) forceUpdate = false;
        updateActiveItem(r);
    }
    
    public void updateActiveItem(int i) {
        oldPos = currentPos;
        oldSongID = currentSongID;
        if (i != -1) currentSongID = (int) songsAdapter.getItemId(i); else currentSongID = -1;
        currentPos = i;
        if (currentPos == oldPos || a.getController() == null || oldSongID == currentSongID) return;
        if (oldSongID != -1 && oldPos != -1) songsAdapter.notifyItemChanged(oldPos, "color");
        if (currentSongID != -1 && currentPos != -1) songsAdapter.notifyItemChanged(currentPos, "color");
    }
    
    public void updateVumeter(boolean b) {
        if (binding.songsList.findViewHolderForAdapterPosition(currentPos+1) instanceof HeaderAdapter.HeaderViewHolder) return;
        SongsListAdapter.ViewHolder v = (SongsListAdapter.ViewHolder) binding.songsList.findViewHolderForAdapterPosition(currentPos+1);
        if (v != null) {
            if (b) {
                VuMeterView view = (VuMeterView) v.itemView.findViewById(R.id.vumeter_view);
                if (view != null) view.resume();
            } else {
                VuMeterView view = (VuMeterView) v.itemView.findViewById(R.id.vumeter_view);
                if (view != null) view.pause();
            }
        }
    }
    
    private void loadSongs() {
        executor.execute(() -> {
            SongMetadataHelper.getAllSongs(getActivity(), new SongLoadListener() {
                @Override
                public void onComplete(ArrayList<Song> list) {
                    if (getActivity() == null) return;
                    SongSorter.sort(list, DataManager.getSongFilterType(), DataManager.isDescendingOrder(), sortedList -> {
                        RuntimeData.songs = sortedList;
                        a.updateSongsQueue(sortedList);
                        size = RuntimeData.songs.size();
                        songsAdapter = new SongsListAdapter(getActivity(), RuntimeData.songs);
                        MainActivity act = (MainActivity) getActivity();
                        HeaderAdapter headerAdapter = new HeaderAdapter();
                        concatAdapter = new ConcatAdapter(headerAdapter, songsAdapter);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                binding.songsList.setAdapter(concatAdapter);
                                binding.swipeRefreshLayout.setRefreshing(false);
                                binding.songsList.setItemAnimator(null);
                                MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
                                msa.setDuration(500);
                                if (binding.emptyLayout.getVisibility() == View.VISIBLE) TransitionManager.beginDelayedTransition(binding.coordinator, msa);
                                binding.emptyLayout.setVisibility(View.GONE);
                                binding.swipeRefreshLayout.setVisibility(View.VISIBLE);
                            }
                        });
                    });
                    
                }
            });
        });
    }
}
