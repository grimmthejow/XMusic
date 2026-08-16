package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.view.View;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricItem;

public class LyricItemDelegate {

    private final LyricItem item;

    private XLyricsLineView mainLineView;
    private XLyricsLineView bgLineView;
    private XLyricsRomajiLineView romajiView;
    private XWaitingDotsView dotsView;

    private int topY = 0;
    private int calculatedHeight = 0;
    private int paddingLeft = 0;
    private int paddingRight = 0;

    private int rawDotsHeight = 0;
    public int rawMainHeight = 0;
    private int rawBgHeight = 0;
    private int rawRomajiHeight = 0;

    private boolean isPrepped = false;
    public boolean isActive = false;
    private float activeProgress = 0f;
    private float activeProgressVelocity = 0f;

    public float staggerY = 0f;
    public float staggerVy = 0f;
    public float stiffness = 120f;

    public LyricItemDelegate(Context context, LyricItem item) {
        this.item = item;
        initViews(context);
    }

    private void initViews(Context context) {
        if (item.mainLine == null) return;

        if (item.mainLine.isWaitingDots) {
            dotsView = new XWaitingDotsView(context);
        } else if (item.mainLine.isBackground) {
            bgLineView = createLineView(context, item.mainLine);
        } else {
            mainLineView = createLineView(context, item.mainLine);
            if (item.linkedBgLine != null) {
                bgLineView = createLineView(context, item.linkedBgLine);
                bgLineView.setTextSize(20);
            }
        }

        if (item.romajiLine != null) {
            romajiView = new XLyricsRomajiLineView(context);
            romajiView.setLyricLine(item.romajiLine);
        }
    }

    private XLyricsLineView createLineView(Context context, LyricLine line) {
        if (line.line != null) {
            String text = line.line.toString();
            for (char c : text.toCharArray()) {
                if ((c >= '\u0600' && c <= '\u06FF') || (c >= '\u0750' && c <= '\u077F')) {
                    XLyricsBidiLineView v = new XLyricsBidiLineView(context);
                    v.setLyricLine(line);
                    return v;
                }
            }
        }
        XLyricsLineView v = new XLyricsLineView(context);
        v.setLyricLine(line);
        return v;
    }

    public void precomputeLayouts(int textWidth) {
        boolean isOpposite = item.mainLine != null && item.mainLine.vocalType != 1;

        if (mainLineView != null) {
            mainLineView.setLineGravity(isOpposite ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL);
            mainLineView.precomputeLayoutData(item.mainLine.line.toString(), textWidth);
        }
        if (bgLineView != null) {
            LyricLine bgSource = item.linkedBgLine != null ? item.linkedBgLine : item.mainLine;
            bgLineView.setLineGravity(isOpposite ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL);
            bgLineView.precomputeLayoutData(bgSource.line.toString(), textWidth);
        }
        if (romajiView != null) {
            romajiView.setLineGravity(Layout.Alignment.ALIGN_NORMAL);
            romajiView.precomputeLayoutData(item.romajiLine.line.toString(), textWidth);
        }
    }

    public void measureStaticBounds(int availableWidth, int parentPaddingLeft, int parentPaddingRight) {
        isPrepped = false;
        int contentWidth = availableWidth - parentPaddingLeft - parentPaddingRight;

        paddingLeft = (int) (contentWidth * 0.05f);
        paddingRight = (int) (contentWidth * 0.2f);

        if (item.mainLine != null && item.mainLine.vocalType != 1 && !item.mainLine.isWaitingDots && !item.mainLine.isBackground) {
            paddingLeft = (int) (contentWidth * 0.2f);
            paddingRight = (int) (contentWidth * 0.05f);
        }

        int textWidth = contentWidth - paddingLeft - paddingRight;

        if (dotsView != null) {
            dotsView.setTimes(item.mainLine.time, item.mainLine.endTime);
            dotsView.measure(
                    View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            rawDotsHeight = dotsView.getMeasuredHeight();
            dotsView.layout(0, 0, textWidth, rawDotsHeight);
        }

        if (mainLineView != null) {
            boolean isOpposite = item.mainLine.vocalType != 1;
            mainLineView.setLineGravity(isOpposite ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL);
            rawMainHeight = mainLineView.getDesiredHeight(item.mainLine.line.toString(), textWidth);
        }

        if (bgLineView != null) {
            LyricLine bgSource = item.linkedBgLine != null ? item.linkedBgLine : item.mainLine;
            boolean isOpposite = item.mainLine.vocalType != 1;
            bgLineView.setLineGravity(isOpposite ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL);
            rawBgHeight = bgLineView.getDesiredHeight(bgSource.line.toString(), textWidth);
        }

        if (romajiView != null) {
            rawRomajiHeight = romajiView.getDesiredHeight(item.romajiLine.line.toString(), textWidth);
        }
    }

    private void prep(int textWidth) {
        if (isPrepped) return;

        if (mainLineView != null) {
            mainLineView.setText(item.mainLine.line.toString(), textWidth);
            mainLineView.measure(
                    View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(rawMainHeight, View.MeasureSpec.EXACTLY)
            );
            mainLineView.layout(0, 0, textWidth, rawMainHeight);
        }

        if (bgLineView != null) {
            LyricLine bgSource = item.linkedBgLine != null ? item.linkedBgLine : item.mainLine;
            bgLineView.setText(bgSource.line.toString(), textWidth);
            bgLineView.measure(
                    View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(rawBgHeight, View.MeasureSpec.EXACTLY)
            );
            bgLineView.layout(0, 0, textWidth, rawBgHeight);
        }

        if (romajiView != null) {
            romajiView.setText(item.romajiLine.line.toString(), textWidth);
            romajiView.measure(
                    View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(rawRomajiHeight, View.MeasureSpec.EXACTLY)
            );
            romajiView.layout(0, 0, textWidth, rawRomajiHeight);
        }

        isPrepped = true;
    }

    public void snapState(boolean active) {
        this.isActive = active;
        this.activeProgress = active ? 1f : 0f;
        this.activeProgressVelocity = 0f;
        this.staggerY = 0f;
        this.staggerVy = 0f;
    }

    public void syncInnerProgress(int progressMs, boolean snap, long parentEndTime) {
        if (dotsView != null) {
            dotsView.updateProgress(progressMs);
        }
        if (mainLineView != null) {
            mainLineView.setCustomEndTime(parentEndTime);
            mainLineView.updateProgress(progressMs, snap);
        }
        if (bgLineView != null) {
            bgLineView.setCustomEndTime(parentEndTime);
            bgLineView.updateProgress(progressMs, snap);
        }
        if (romajiView != null) {
            romajiView.updateProgress(progressMs, snap);
        }
    }

    public void updatePhysics(float dt) {
        float targetProgress = isActive ? 1f : 0f;
        float progDisplacement = activeProgress - targetProgress;

        float progAccel = (-140f * progDisplacement) - (20f * activeProgressVelocity);
        activeProgressVelocity += progAccel * dt;
        activeProgress += activeProgressVelocity * dt;

        if (!isActive && activeProgress < 0.001f && Math.abs(activeProgressVelocity) < 0.01f) {
            activeProgress = 0f;
            activeProgressVelocity = 0f;
        } else if (isActive && activeProgress > 0.999f && Math.abs(activeProgressVelocity) < 0.01f) {
            activeProgress = 1f;
            activeProgressVelocity = 0f;
        }

        if (Math.abs(staggerY) > 0.1f || Math.abs(staggerVy) > 0.1f) {
            float dampingRatio = 0.85f;
            float damping = 2f * dampingRatio * (float) Math.sqrt(stiffness);
            float accel = (-stiffness * staggerY) - (damping * staggerVy);
            staggerVy += accel * dt;
            staggerY += staggerVy * dt;
        } else {
            staggerY = 0f;
            staggerVy = 0f;
        }
    }

    public void updateDynamicHeights(float density) {
        int h = 0;
        int topBotPad = (int) (4 * density);

        if (dotsView != null) {
            h += (int) (rawDotsHeight * activeProgress);
        }
        if (mainLineView != null) {
            h += topBotPad + rawMainHeight;
        }
        if (romajiView != null) {
            h += (int) (rawRomajiHeight * activeProgress) - (int) (8 * density * activeProgress);
        }
        if (bgLineView != null) {
            h += (int) (rawBgHeight * activeProgress);
        }
        if (mainLineView != null) {
            h += topBotPad;
        }

        calculatedHeight = h;
    }

    public void draw(Canvas canvas, int globalPaddingLeft, float density, int availableWidth) {
        if (calculatedHeight <= 0) return;

        int textWidth = availableWidth - globalPaddingLeft - paddingLeft - paddingRight;
        prep(textWidth);

        int saveCount = canvas.save();
        float currentY = topY + staggerY;

        boolean isOpposite = item.mainLine != null && item.mainLine.vocalType != 1;
        float pivotX = isOpposite ? textWidth : 0f;

        float globalScale = 1.0f + 0.015f * activeProgress;
        canvas.scale(globalScale, globalScale, globalPaddingLeft + availableWidth / 2f, topY + calculatedHeight / 2f);

        float dotsP = Math.max(0f, activeProgress);
        float bgP = Math.max(0f, activeProgress);
        float romajiP = Math.max(0f, activeProgress);

        float currentDotsH = rawDotsHeight * dotsP;
        float currentBgH = rawBgHeight * bgP;
        float currentRomajiH = rawRomajiHeight * romajiP;

        float dotsY = currentY;
        float mainY = dotsY + currentDotsH + (4 * density);

        float romajiY = mainY + rawMainHeight - (8 * density);
        float bgY = romajiY + currentRomajiH;

        if (mainLineView != null) {
            canvas.save();
            canvas.translate(globalPaddingLeft + paddingLeft, mainY);

            float mainScaleFactor = 1.0f + (0.02f * activeProgress);
            canvas.scale(mainScaleFactor, mainScaleFactor, pivotX, rawMainHeight / 2f);

            float mainAlphaFloat = 0.35f + 0.65f * activeProgress;
            int mainAlpha = (int) (255 * mainAlphaFloat);
            if (mainAlpha < 255) canvas.saveLayerAlpha(-200f, -200f, textWidth + 200f, rawMainHeight + 200f, mainAlpha);

            mainLineView.draw(canvas);

            if (mainAlpha < 255) canvas.restore();
            canvas.restore();
        }

        if (romajiView != null && currentRomajiH > 0) {
            canvas.save();
            float slideY = romajiY - (rawRomajiHeight * (1f - romajiP));
            canvas.translate(globalPaddingLeft + paddingLeft, slideY);

            float scale = 0.7f + 0.3f * romajiP;
            canvas.scale(scale, scale, pivotX, 0f);

            float alphaP = Math.max(0f, (romajiP - 0.4f) / 0.6f);
            int romajiAlpha = (int) (255 * (0.65f * alphaP));

            if (romajiAlpha > 0) {
                if (romajiAlpha < 255) canvas.saveLayerAlpha(-200f, -200f, textWidth + 200f, rawRomajiHeight + 200f, romajiAlpha);
                romajiView.draw(canvas);
                if (romajiAlpha < 255) canvas.restore();
            }
            canvas.restore();
        }

        if (bgLineView != null && currentBgH > 0) {
            canvas.save();
            float slideY = bgY - (rawBgHeight * (1f - bgP));
            canvas.translate(globalPaddingLeft + paddingLeft, slideY);

            float scale = 0.7f + 0.3f * bgP;
            canvas.scale(scale, scale, pivotX, 0f);

            float alphaP = Math.max(0f, (bgP - 0.4f) / 0.6f);
            int bgAlpha = (int) (255 * (0.65f * alphaP));

            if (bgAlpha > 0) {
                if (bgAlpha < 255) canvas.saveLayerAlpha(-200f, -200f, textWidth + 200f, rawBgHeight + 200f, bgAlpha);
                bgLineView.draw(canvas);
                if (bgAlpha < 255) canvas.restore();
            }
            canvas.restore();
        }

        if (dotsView != null && currentDotsH > 0) {
            canvas.save();
            float slideY = dotsY - (rawDotsHeight * (1f - dotsP));
            canvas.translate(globalPaddingLeft + paddingLeft, slideY);

            float scale = 0.7f + 0.3f * dotsP;
            canvas.scale(scale, scale, textWidth / 2f, rawDotsHeight / 2f);

            float alphaP = Math.max(0f, (dotsP - 0.4f) / 0.6f);
            int dotsAlpha = (int) (255 * alphaP);

            if (dotsAlpha > 0) {
                if (dotsAlpha < 255) canvas.saveLayerAlpha(-200f, -200f, textWidth + 200f, rawDotsHeight + 200f, dotsAlpha);
                dotsView.draw(canvas);
                if (dotsAlpha < 255) canvas.restore();
            }
            canvas.restore();
        }

        canvas.restoreToCount(saveCount);
    }

    public void setTop(int y) {
        this.topY = y;
    }

    public int getTop() {
        return topY;
    }

    public int getBottom() {
        return topY + calculatedHeight;
    }

    public int getLiveHeight() {
        return calculatedHeight;
    }

    public LyricItem getItem() {
        return item;
    }

    public void setLyricColor(int color) {
        if (dotsView != null) dotsView.setPrimaryColor(color);
        if (mainLineView != null) mainLineView.setLyricColor(color);
        if (bgLineView != null) bgLineView.setLyricColor(color);
        if (romajiView != null) romajiView.setLyricColor(color);
    }

    public void setFontConfig(String config) {
        if (mainLineView != null) mainLineView.setFontConfig(config);
        if (bgLineView != null) bgLineView.setFontConfig(config);
        if (romajiView != null) romajiView.setFontConfig(config);
    }

    public void setEnableSparkles(boolean enableSparkles) {
        if (mainLineView != null) mainLineView.setEnableSparkles(enableSparkles);
        if (bgLineView != null) bgLineView.setEnableSparkles(enableSparkles);
        if (romajiView != null) romajiView.setEnableSparkles(enableSparkles);
    }
}