package com.xapps.media.xmusic.fragment;

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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialSplitButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.listitem.ListItemViewHolder;
import com.google.android.material.transition.MaterialSharedAxis;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.common.SongLoadListener;
import com.xapps.media.xmusic.data.DataManager;
import com.xapps.media.xmusic.data.RuntimeData;
import com.xapps.media.xmusic.databinding.*;
import com.xapps.media.xmusic.databinding.ActivityMainBinding;
import com.xapps.media.xmusic.helper.SongMetadataHelper;
import com.xapps.media.xmusic.helper.SongSorter;
import com.xapps.media.xmusic.service.PlayerService;
import com.xapps.media.xmusic.utils.*;
import com.xapps.media.xmusic.widget.VuMeterView;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kotlin.Unit;
import me.zhanghai.android.fastscroll.FastScroller;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class MusicListFragment extends BaseFragment {
	
	public MusicListFragmentBinding binding;
    private int currentSongID, oldSongID = -1;
    private int oldPos = -1;
    private int currentPos= -1;
	public final Fragment f = this;
	private String Title = "";
	private String Artitst = "";
	private String coverUri = "";
	public int imageSize, size;
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
    
    public static FloatingActionButton fab;
    
    private int lastSpacing;
    private int defaultFabMargin;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
        a = (MainActivity) getActivity();
		binding = MusicListFragmentBinding.inflate(_inflater, _container, false);
        mainHandler = new Handler(Looper.getMainLooper());
        if (getActivity() != null)binding.collapsingToolbar.setPadding(0, XUtils.getStatusBarHeight(getActivity()), 0, 0);
		initializeLogic();
        setUpListeners(); 
		return binding.getRoot();
	}
	
	private void initializeLogic() {
        loadSongs();
        fab = binding.shuffleButton;
        a = (MainActivity) getActivity();
        activity = a.getBinding();
        placeholder = ContextCompat.getDrawable(getActivity(), R.drawable.placeholder_small);
        imageSize = XUtils.convertToPx(getActivity(), 45f);
        activity.bottomNavigation.post(() -> {
            if (getActivity() == null) return;
            a.setMusicListFragmentInstance(this);
            lastSpacing = XUtils.convertToPx(getActivity(), 5f) + activity.miniPlayerDetailsLayout.getHeight()*2 + activity.bottomNavigation.getHeight();
            binding.songsList.addItemDecoration(new BottomSpacingDecoration(lastSpacing));
            binding.songsList.setLayoutManager(new LinearLayoutManager(getContext()));
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
        binding.topTitle.setOnClickListener(v -> {
            songsAdapter.notifyDataSetChanged();
            XUtils.showMessage(getActivity(), "done");
        });
        
        
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            forceUpdate = true;
            SongMetadataHelper.clearCachedList();
            loadSongs();
            a.loadSongs();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                binding.swipeRefreshLayout.setRefreshing(false);
            }, 2000);
        });
    }
	
	public class SongsListAdapter extends RecyclerView.Adapter<SongsListAdapter.ViewHolder> {
		
        int c1 = MaterialColorUtils.colorPrimary;
        int c2 = MaterialColorUtils.colorSecondary;
        int c3 = MaterialColorUtils.colorOnSurface;
        int c4 = MaterialColorUtils.colorOutline;
        
        private ArrayList<HashMap<String, Object>> data = new ArrayList();
        
        private int spacing;
        private int resId = R.drawable.placeholder_small;
        private Uri uri;
        private String placeholderUri;
        
        private static final int TYPE_SINGLE = -1;
        private static final int TYPE_TOP = 0;
        private static final int TYPE_MIDDLE = 1;
        private static final int TYPE_BOTTOM = 2;
        
        private SongItemMiddleBinding binding;
        
		public SongsListAdapter(Context c, ArrayList<HashMap<String, Object>> arraylist) {
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
		    coverUri = data.get(position).get("thumbnail") == null? "invalid" : data.get(position).get("thumbnail").toString();
		    Title = data.get(position).get("title").toString();
		    Artitst = data.get(position).get("author").toString();
		    Glide.with(f)
		    .load(coverUri.equals("invalid")? placeholder : Uri.parse("file://"+coverUri))
		    .centerCrop()  
            .fallback(placeholder)  
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)  
            .transition(DrawableTransitionOptions.withCrossFade())  
            .placeholder(placeholder)  
            .override(imageSize, imageSize)
		    .into(binding.songCover);
		    if (Title == null || Title.equals("")) {
			    binding.SongTitle.setText("Unknown");
		    } else {
			    binding.SongTitle.setText(Title);
			    binding.SongArtist.setText(Artitst);
		    }

		    binding.item.setOnClickListener(v -> {  
                if (a.getController() == null ) return;  
                if (a.bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_SETTLING || a.bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_DRAGGING) return;  
                long currentTime = System.currentTimeMillis();  
                if (currentTime - lastClickTime < DEBOUNCE_MS) {  
                    return;  
                }  
                lastClickTime = currentTime;  
                activity.miniPlayerBottomSheet.setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.rounded_corners_bottom_sheet));  
             
                a.setSong(position, forceUpdate);  
                if (forceUpdate) forceUpdate = false;  
                updateActiveItem(position);  
            });  
            binding.optionsIcon.setOnClickListener(v -> {  
                BottomSheetDragHandleView drag = new BottomSheetDragHandleView(getActivity());  
                LinearLayout bsl = (LinearLayout)getActivity().getLayoutInflater().inflate(R.layout.options_bottom_sheet, null);  
                bsl.addView(drag, 0);  
                BottomSheetDialog bsd = new BottomSheetDialog(getActivity());  
                bsd.setContentView(bsl, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));  
                bsd.show();  
            });

	    }
        
        @Override
        public int getItemCount() {
            return data.size();
        }
        
        @Override
        public long getItemId(int position) {
            String path = data.get(position).get("path").toString();
            return path.hashCode();
        }
        
        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            super.onViewRecycled(holder);
            Glide.with(holder.itemView.getContext()).clear((View)holder.itemView.findViewById(R.id.songCover)); 
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
		
		static class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
        
        public void updateData(ArrayList<HashMap<String, Object>> a) {
            data = a;
            notifyDataSetChanged();
        }
	}

    public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder> {
	    @Override
	    public HeaderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		    View headerView = LayoutInflater.from(parent.getContext()).inflate(R.layout.header_view, parent, false);
		    return new HeaderViewHolder(headerView);
	    }
		
	    @Override
		public void onBindViewHolder(HeaderViewHolder holder, int position) {
            activity.bottomNavigation.post(() -> {
                if (scroller == null) scroller = new FastScrollerBuilder(binding.songsList).useMd2Style().setPadding(0, XUtils.convertToPx(getActivity(), 18f) + holder.itemView.getHeight(), 0, activity.bottomNavigation.getHeight()+XUtils.getNavigationBarHeight(getActivity())).build();
            });
			View view = holder.itemView;
			TextView sg = (TextView) view.findViewById(R.id.songs_count);
			sg.setText("0 Songs".replace("0",String.valueOf(size)));
            MaterialButton orderButton = (MaterialButton) view.findViewById(R.id.order_type_button);
            orderButton.setChecked(!DataManager.isDescendingOrder());
            MaterialButton filterButton = (MaterialButton) view.findViewById(R.id.sort_filter_button);
            MaterialSplitButton msb = (MaterialSplitButton) view.findViewById(R.id.split_button);
            orderButton.setOnClickListener(v -> {
                boolean b = orderButton.isChecked();
                DataManager.setDescendingOrder(!b);
                SongSorter.sort(RuntimeData.songsMap, DataManager.getSongFilterType(), !b, sortedMap -> {
                    RuntimeData.songsMap = sortedMap;
                    songsAdapter.updateData(sortedMap);
                    a.updateSongsQueue(RuntimeData.songsMap);
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
                    case BITRATE :
                        b.eleventhItem.setChecked(true);
                        b.eleventhRadio.setChecked(true);
                    break;
                }
                b.firstItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.TITLE);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.TITLE, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.secondItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ARTIST);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.ARTIST, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.thirdItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ALBUM);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.ALBUM, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.fourthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.ALBUM_ARTIST);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.ALBUM_ARTIST, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.fifthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.YEAR);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.YEAR, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.sixthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.TRACK);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.TRACK, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.seventhItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DURATION);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.DURATION, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.eighthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DATE_ADDED);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.DATE_ADDED, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.ninethItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.DATE_MODIFIED);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.DATE_MODIFIED, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.tenthItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.SIZE);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.SIZE, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                b.eleventhItem.setOnClickListener(v2 -> {
                    DataManager.setSongFilterType(SongSorter.SortBy.BITRATE);
                    SongSorter.sort(RuntimeData.songsMap, SongSorter.SortBy.BITRATE, DataManager.isDescendingOrder(), sortedMap -> {
                        RuntimeData.songsMap = sortedMap;
                        songsAdapter.updateData(sortedMap);
                        a.updateSongsQueue(RuntimeData.songsMap);
                    });
                    bs.dismiss();
                });
                bs.setTitle("Sort By");
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
        int r = new Random().nextInt((RuntimeData.songsMap.size()-1 - 0) + 1) + 0;
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
                public void onComplete(ArrayList<HashMap<String, Object>> map) {
                    if (getActivity() == null) return;
                    SongSorter.sort(map, DataManager.getSongFilterType(), DataManager.isDescendingOrder(), sortedList -> {
                        RuntimeData.songsMap = sortedList;
                        a.updateSongsQueue(sortedList);
                        size = RuntimeData.songsMap.size();
                        songsAdapter = new SongsListAdapter(getActivity(), RuntimeData.songsMap);
                        MainActivity act = (MainActivity) getActivity();
                        HeaderAdapter headerAdapter = new HeaderAdapter();
                        concatAdapter = new ConcatAdapter(headerAdapter, songsAdapter);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                binding.songsList.setAdapter(concatAdapter);
                                binding.songsList.setItemAnimator(null);
                                MaterialSharedAxis msa = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
                                msa.setDuration(500);
                                if (binding.emptyLayout.getVisibility() == View.VISIBLE) TransitionManager.beginDelayedTransition(binding.coordinator, msa);
                                binding.emptyLayout.setVisibility(View.GONE);
                                binding.swipeRefreshLayout.setVisibility(View.VISIBLE);
                                ViewKt.doOnLayout(binding.collapsingToolbar, v -> {
                                    binding.shuffleButton.setTranslationY(v.getHeight() / 2f);
                                    binding.shuffleButton.show();
                                    return Unit.INSTANCE;
                                });
                                
                            }
                        });
                    });
                    
                }
                    
                @Override
                public void onProgress(ArrayList<HashMap<String, Object>> map, int count) {
                        
                }
            });
        });
    }
}
