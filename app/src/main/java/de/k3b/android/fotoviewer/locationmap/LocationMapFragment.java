package de.k3b.android.fotoviewer.locationmap;


import android.app.DialogFragment;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;


import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPointE6;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayManager;

import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import de.k3b.android.fotoviewer.Global;
import de.k3b.android.fotoviewer.R;
import de.k3b.android.fotoviewer.queries.FotoSql;
import de.k3b.android.fotoviewer.queries.QueryParameterParcelable;
import de.k3b.android.osmdroid.DefaultResourceProxyImplEx;
import de.k3b.android.osmdroid.FolderOverlay;
import de.k3b.android.osmdroid.IconFactory;
import de.k3b.android.osmdroid.MarkerBase;
import de.k3b.io.GeoRectangle;

/**
 * A fragment to display Foto locations in a geofrafic map.
 * A location-area can be picked for filtering.
 * A simple {@link Fragment} subclass.
 */
public class LocationMapFragment extends DialogFragment {

    // for debugging
    private static int id = 1;
    private final String debugPrefix;

    private MapView mMapView;
    private FolderOverlay mFolderOverlay;
    IconFactory mIconFactory = null;
    private DefaultResourceProxyImplEx mResourceProxy;

    /**
     * setCenterZoom does not work in onCreate() because getHeight() and getWidth() are not calculated yet and return 0;
     * setCenterZoom must be set later when getHeight() and getWith() are known (i.e. in onWindowFocusChanged()).
     * <p/>
     * see http://stackoverflow.com/questions/10411975/how-to-get-the-width-and-height-of-an-image-view-in-android/10412209#10412209
     */
    private BoundingBoxE6 mDelayedZoomToBoundingBox = null;
    private SeekBar mZoomBar;

    public LocationMapFragment() {
        // Required empty public constructor
        debugPrefix = "LocationMapFragment#" + (id++)  + " ";
        Global.debugMemory(debugPrefix, "ctor");
        // Required empty public constructor
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, debugPrefix + "()");
        }
    }

    @Override public void onDestroy() {
        if (mRecycler != null) mRecycler.empty();
        super.onDestroy();
        // RefWatcher refWatcher = FotoGalleryApp.getRefWatcher(getActivity());
        // refWatcher.watch(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_location_map, container, false);

        mMapView = (MapView) view.findViewById(R.id.mapview);
        createZoomBar(view);
        mMapView.setMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                reload();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                mZoomBar.setProgress(mMapView.getZoomLevel());

                reload();
                return false;
            }
        });

        mResourceProxy = new DefaultResourceProxyImplEx(getActivity().getApplicationContext());

        mIconFactory = new IconFactory(mResourceProxy, getResources().getDrawable(R.drawable.marker_green));
        final List<Overlay> overlays = this.mMapView.getOverlays();

        mFolderOverlay = createFolderOverlay(overlays);

        // mFolderOverlay.add(createMarker(mMapView, ...));

        if (getShowsDialog()) {
            Button cmdOk = (Button) view.findViewById(R.id.ok);
            cmdOk.setVisibility(View.VISIBLE);
            cmdOk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // TODO
                    /*
                    if (currentSelectedPosition != null) {

                        GeoPointDto geoPoint = new GeoPointDto().setLatitude(currentSelectedPosition.getPosition().getLatitude()).setLongitude(currentSelectedPosition.getPosition().getLongitude());
                        String uri = new GeoUri(GeoUri.OPT_DEFAULT).toUriString(geoPoint);
                        setResult(0, new Intent(Intent.ACTION_PICK, Uri.parse(uri)));
                    }

                    finish();
                */
                }
            });
        }

        if (this.mDelayedZoomToBoundingBox != null) {
            mMapView.zoomToBoundingBox(this.mDelayedZoomToBoundingBox);
            this.mDelayedZoomToBoundingBox = null;
        }

        return view;
    }

    private void createZoomBar(View view) {
        mMapView.setBuiltInZoomControls(true);

        mZoomBar = (SeekBar) view.findViewById(R.id.zoomBar);

        mZoomBar.setMax(mMapView.getMaxZoomLevel() - mMapView.getMinZoomLevel());
        mZoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser)
                    mMapView.getController().setZoom(progress - mMapView.getMinZoomLevel());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }


    private FolderOverlay createFolderOverlay(List<Overlay> overlays) {
        FolderOverlay result = new FolderOverlay(this.getActivity());
        overlays.add(result);

        return result;
    }

    public void defineNavigation(GeoRectangle filter, int queryType) {
        BoundingBoxE6 boundingBox = new BoundingBoxE6(
                filter.getLatitudeMax(),
                filter.getLogituedMin(),
                filter.getLatitudeMin(),
                filter.getLogituedMax());

        if (this.mMapView != null) {
            this.mMapView.zoomToBoundingBox(boundingBox);
        } else {
            this.mDelayedZoomToBoundingBox = boundingBox;
        }
    }

    /** all marker clicks will be delegated to LocationMapFragment#onMarkerClicked() */
    private class FotoMarker extends MarkerBase<Object> {

        public FotoMarker(ResourceProxy pResourceProxy) {
            super(pResourceProxy);
        }

        /**
         * @return true if click was handeled.
         */
        @Override
        protected boolean onMarkerClicked(MapView mapView, int markerId, IGeoPointE6 makerPosition, Object markerData) {
            return LocationMapFragment.this.onMarkerClicked(markerId, makerPosition, markerData);
        }
    }

    /** The factory LocationMapFragment.FotoMarkerLoaderTask#createMarker() tries to recycle old
     *     unused Fotomarkers before creating new */
    private Stack<FotoMarker> mRecycler = new Stack<FotoMarker>();

    private void reload() {
        if (mCurrentLoader == null) {
            // not active yet
            List<Overlay> oldItems = mFolderOverlay.getItems();

            mLastZoom = this.mMapView.getZoomLevel();
            int groupingFactor = getGroupingFactor(mLastZoom);
            BoundingBoxE6 world = this.mMapView.getBoundingBox();

            reload(world, groupingFactor, oldItems);
        } else {
            mPendingLoads++;
        }
    }

    private void reload(BoundingBoxE6 latLonArea, int groupingFactor, List<Overlay> oldItems) {
        QueryParameterParcelable query = FotoSql.getQueryGroupByPlace(groupingFactor);
        query.clearWhere();

        FotoSql.addWhereFilteLatLon(query
                , latLonArea.getLatSouthE6() * 1E-6
                , latLonArea.getLatNorthE6() * 1E-6
                , latLonArea.getLonEastE6() * 1E-6
                , latLonArea.getLonWestE6() * 1E-6);

        HashMap<Integer, FotoMarker> oldItemsHash = new HashMap<Integer, FotoMarker>();
        for (Overlay o : oldItems) {
            FotoMarker marker = (FotoMarker) o;
            oldItemsHash.put(marker.getID(), marker);
        }

        mCurrentLoader = new FotoMarkerLoaderTask(oldItemsHash);
        mCurrentLoader.execute(query);
    }

    /** translates map-zoomlevel to groupfactor
     * that tells sql how geo-points are grouped together.
     */
    private int getGroupingFactor(int zoomlevel) {
        // todo
        return 100;
    }

    // for debugginc
    private static int sInstanceCountFotoLoader = 1;

    private int mLastZoom = -1;
    private int mPendingLoads = 0;
    private FotoMarkerLoaderTask mCurrentLoader = null;
    private class FotoMarkerLoaderTask extends MarkerLoaderTask<FotoMarker> {

        public FotoMarkerLoaderTask(HashMap<Integer, FotoMarker> oldItems) {
            super(getActivity(), debugPrefix + "-MarkerLoader#" + sInstanceCountFotoLoader++, oldItems);
        }

        @Override
        protected FotoMarker createMarker() {
            FotoMarker marker = (mRecycler.isEmpty()) ? new FotoMarker(mResourceProxy) : mRecycler.pop();
            return marker;
        }

        // This is called when doInBackground() is finished
        protected void onPostExecute(OverlayManager result) {
            boolean zoomLevelChanged = mMapView.getZoomLevel() != mLastZoom;

            if (isCancelled()) {
                onLoadFinished(null, zoomLevelChanged);
            } else {
                onLoadFinished(result, zoomLevelChanged);

                recyleItems(zoomLevelChanged, mOldItems);
            }
            mOldItems.clear();
            mOldItems = null;

            // in the meantime the mapview has moved: must recalculate again.
            mCurrentLoader = null;
            if (mPendingLoads > 0) {
                mPendingLoads = 0;
                reload();
            }
        }

        private void recyleItems(boolean zoomLevelChanged, HashMap<Integer, FotoMarker> unusedItems) {
            if (zoomLevelChanged) {

            } else {
                // unused old items go into recycler
                for (Integer id : unusedItems.keySet()) {
                    FotoMarker marker = unusedItems.get(id);
                    marker.set(0, null, null, null);
                    mRecycler.add(marker);
                }
            }
        }
    }

    /** gets called when FotoMarkerLoaderTask has finished.
     *
     * @param result null if there was an error
     * @param zoomLevelChanged
     */
    private void onLoadFinished(OverlayManager result, boolean zoomLevelChanged) {
        if (Global.debugEnabled) {
            int found = (result != null) ? result.size() : 0;
            Log.i(Global.LOG_CONTEXT, debugPrefix + "onLoadFinished() markers created: " + found);
        }

        if (result != null) {
            OverlayManager old = mFolderOverlay.setOverlayManager(result);
            if (old != null) {
                if (zoomLevelChanged) {
                    for (Overlay item : old) {
                        mRecycler.add((FotoMarker) item);
                    }
                }
                old.onDetach(this.mMapView);
                old.clear();
            }
            this.mMapView.invalidate();
        }
    }

    /**
     * @return true if click was handeled.
     */
    private boolean onMarkerClicked(int markerId, IGeoPointE6 makerPosition, Object markerData) {
        return false; // TODO
    }
}