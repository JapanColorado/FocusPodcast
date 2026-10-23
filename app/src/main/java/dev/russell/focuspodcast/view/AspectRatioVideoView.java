package dev.russell.focuspodcast.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * The surface the video player renders into. It measures itself to the largest size with the
 * video's aspect ratio that fits the space its parent offers, so the picture is letterboxed
 * rather than stretched; until the video size is known it fills that space.
 */
public class AspectRatioVideoView extends SurfaceView {

    private int videoWidth;
    private int videoHeight;

    public AspectRatioVideoView(Context context) {
        this(context, null);
    }

    public AspectRatioVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AspectRatioVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** Sets the display size of the video (non-square pixels already applied). */
    public void setVideoSize(int width, int height) {
        if (width == videoWidth && height == videoHeight) {
            return;
        }
        videoWidth = width;
        videoHeight = height;
        requestLayout();
    }

    /** Width divided by height of the video, or 0 while it is unknown. */
    public float getVideoAspectRatio() {
        return videoWidth > 0 && videoHeight > 0 ? (float) videoWidth / videoHeight : 0f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (videoWidth <= 0 || videoHeight <= 0 || availableWidth <= 0 || availableHeight <= 0) {
            setMeasuredDimension(availableWidth, availableHeight);
            return;
        }
        float videoRatio = (float) videoWidth / videoHeight;
        float availableRatio = (float) availableWidth / availableHeight;
        if (videoRatio > availableRatio) {
            setMeasuredDimension(availableWidth, Math.round(availableWidth / videoRatio));
        } else {
            setMeasuredDimension(Math.round(availableHeight * videoRatio), availableHeight);
        }
    }
}
