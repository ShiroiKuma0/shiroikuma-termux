package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * shiroikuma-termux (pill menus, UI page): the icons of menus, rows, section headings and chips, drawn in code as stroke line-art on a 24-unit
 * grid — the app ships next to no icon assets, and a drawn glyph takes any role colour and any
 * size without a resource per density.
 *
 * <p>One instance draws one glyph ({@code ICON_*}) in one colour; {@link #setTintList} replaces
 * that colour, so an {@code ImageView.setImageTintList} works on it exactly as on a vector.
 */
public final class ShiroikumaIcons extends Drawable {

    public static final int ICON_DOT = 0;
    public static final int ICON_MENU = 1;
    public static final int ICON_LINK = 2;
    public static final int ICON_SHARE = 3;
    public static final int ICON_PERSON = 4;
    public static final int ICON_KEY = 5;
    public static final int ICON_RESET = 6;
    public static final int ICON_KILL = 7;
    public static final int ICON_SLIDERS = 8;
    public static final int ICON_SUN = 9;
    public static final int ICON_HELP = 10;
    public static final int ICON_GEAR = 11;
    public static final int ICON_WARNING = 12;
    public static final int ICON_COPY = 13;
    public static final int ICON_SAVE = 14;
    public static final int ICON_CANCEL = 15;
    /** The › of a row that opens something. */
    public static final int ICON_CHEVRON = 16;
    // --- the UI page: rows, section headings, chips ---
    public static final int ICON_GLOBE = 17;
    public static final int ICON_DROP = 18;
    public static final int ICON_TEXT = 19;
    public static final int ICON_SIZE = 20;
    public static final int ICON_DIR = 21;
    public static final int ICON_CLOCK = 22;
    public static final int ICON_LOCK = 23;
    public static final int ICON_BRANCH = 24;
    public static final int ICON_BELL = 25;
    public static final int ICON_WINDOW = 26;
    public static final int ICON_GRID = 27;
    public static final int ICON_BATTERY = 28;
    public static final int ICON_LAYERS = 29;
    public static final int ICON_SORT = 30;
    public static final int ICON_TERMINAL = 31;
    public static final int ICON_KEYBOARD = 32;
    public static final int ICON_BAR = 33;
    public static final int ICON_DIALOG = 34;
    public static final int ICON_PLUG = 35;
    public static final int ICON_TOGGLE = 36;
    public static final int ICON_FRAME = 37;
    public static final int ICON_CORNER = 38;
    public static final int ICON_SQUARE = 39;
    public static final int ICON_EXPORT = 40;
    public static final int ICON_ADD = 41;
    public static final int ICON_PAGE = 42;
    /** The ⌄ of an open section. */
    public static final int ICON_CHEVRON_DOWN = 43;

    private static final float GRID = 24f;
    private static final float STROKE = 1.8f;

    private final int mIcon;
    private final int mSizePx;
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();

    public ShiroikumaIcons(@NonNull Context context, int icon, int color) {
        this(context, icon, color, 24);
    }

    public ShiroikumaIcons(@NonNull Context context, int icon, int color, int sizeDp) {
        this(icon, color, ShiroikumaDialogs.dp(context, sizeDp), true);
    }

    /** An icon of an exact pixel size — the icon of a label, sized by that label's text. */
    @NonNull
    public static ShiroikumaIcons ofPx(int icon, int color, int sizePx) {
        return new ShiroikumaIcons(icon, color, sizePx, true);
    }

    private ShiroikumaIcons(int icon, int color, int sizePx, boolean px) {
        mIcon = icon;
        mSizePx = sizePx;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mFill.setStyle(Paint.Style.FILL);
        setColor(color);
    }

    public void setColor(int color) {
        mStroke.setColor(color);
        mFill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float side = Math.min(b.width(), b.height());
        if (side <= 0) return;
        int save = c.save();
        c.translate(b.left + (b.width() - side) / 2f, b.top + (b.height() - side) / 2f);
        c.scale(side / GRID, side / GRID);
        glyph(c);
        c.restoreToCount(save);
    }

    private void glyph(@NonNull Canvas c) {
        Path p = mPath;
        p.rewind();
        switch (mIcon) {
            case ICON_MENU:
                c.drawLine(4, 7, 20, 7, mStroke);
                c.drawLine(4, 12, 20, 12, mStroke);
                c.drawLine(4, 17, 20, 17, mStroke);
                break;
            case ICON_LINK:
                c.rotate(-45, 12, 12);
                mRect.set(2, 8.5f, 11, 15.5f);
                c.drawRoundRect(mRect, 3.5f, 3.5f, mStroke);
                mRect.set(13, 8.5f, 22, 15.5f);
                c.drawRoundRect(mRect, 3.5f, 3.5f, mStroke);
                c.drawLine(8, 12, 16, 12, mStroke);
                break;
            case ICON_SHARE:
                c.drawCircle(18, 5, 2.4f, mStroke);
                c.drawCircle(6, 12, 2.4f, mStroke);
                c.drawCircle(18, 19, 2.4f, mStroke);
                c.drawLine(8.1f, 10.8f, 15.9f, 6.2f, mStroke);
                c.drawLine(8.1f, 13.2f, 15.9f, 17.8f, mStroke);
                break;
            case ICON_PERSON:
                c.drawCircle(12, 8, 3.6f, mStroke);
                p.moveTo(4.5f, 20);
                p.cubicTo(4.5f, 15.4f, 8, 14, 12, 14);
                p.cubicTo(16, 14, 19.5f, 15.4f, 19.5f, 20);
                c.drawPath(p, mStroke);
                break;
            case ICON_KEY:
                c.drawCircle(7.5f, 12, 3.8f, mStroke);
                c.drawLine(11.3f, 12, 21, 12, mStroke);
                c.drawLine(17.5f, 12, 17.5f, 15.5f, mStroke);
                c.drawLine(21, 12, 21, 15, mStroke);
                break;
            case ICON_RESET:
                mRect.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawArc(mRect, 40, 288, false, mStroke);
                p.moveTo(19.5f, 3.5f);
                p.lineTo(19.5f, 8);
                p.lineTo(15, 8);
                c.drawPath(p, mStroke);
                break;
            case ICON_KILL:
                c.drawCircle(12, 12, 8.5f, mStroke);
                c.drawLine(9, 9, 15, 15, mStroke);
                c.drawLine(15, 9, 9, 15, mStroke);
                break;
            case ICON_SLIDERS:
                slider(c, 6, 15);
                slider(c, 12, 8);
                slider(c, 18, 16);
                break;
            case ICON_SUN:
                c.drawCircle(12, 12, 4, mStroke);
                rays(c, 6.8f, 9.4f);
                break;
            case ICON_HELP:
                c.drawCircle(12, 12, 8.5f, mStroke);
                p.moveTo(9.4f, 9.6f);
                p.cubicTo(9.4f, 6.4f, 14.6f, 6.4f, 14.6f, 9.6f);
                p.cubicTo(14.6f, 11.6f, 12, 11.7f, 12, 14);
                c.drawPath(p, mStroke);
                c.drawCircle(12, 17, 1.1f, mFill);
                break;
            case ICON_GEAR:
                c.drawCircle(12, 12, 2.8f, mStroke);
                c.drawCircle(12, 12, 6.6f, mStroke);
                rays(c, 6.6f, 9.2f);
                break;
            case ICON_WARNING:
                p.moveTo(12, 3.5f);
                p.lineTo(21.5f, 20);
                p.lineTo(2.5f, 20);
                p.close();
                c.drawPath(p, mStroke);
                c.drawLine(12, 9.5f, 12, 14.3f, mStroke);
                c.drawCircle(12, 17.2f, 1.1f, mFill);
                break;
            case ICON_COPY:
                mRect.set(8, 8, 20, 20);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                p.moveTo(16, 4);
                p.lineTo(4, 4);
                p.lineTo(4, 16);
                c.drawPath(p, mStroke);
                break;
            case ICON_SAVE:
                c.drawLine(12, 4, 12, 15, mStroke);
                p.moveTo(7.5f, 10.5f);
                p.lineTo(12, 15);
                p.lineTo(16.5f, 10.5f);
                p.moveTo(4.5f, 16);
                p.lineTo(4.5f, 20);
                p.lineTo(19.5f, 20);
                p.lineTo(19.5f, 16);
                c.drawPath(p, mStroke);
                break;
            case ICON_CANCEL:
                c.drawLine(6, 6, 18, 18, mStroke);
                c.drawLine(18, 6, 6, 18, mStroke);
                break;
            case ICON_CHEVRON:
                p.moveTo(9.5f, 6.5f);
                p.lineTo(15, 12);
                p.lineTo(9.5f, 17.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_CHEVRON_DOWN:
                p.moveTo(6.5f, 9.5f);
                p.lineTo(12, 15);
                p.lineTo(17.5f, 9.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_GLOBE:
                c.drawCircle(12, 12, 8.5f, mStroke);
                mRect.set(8.2f, 3.5f, 15.8f, 20.5f);
                c.drawOval(mRect, mStroke);
                c.drawLine(3.5f, 12, 20.5f, 12, mStroke);
                break;
            case ICON_DROP:
                p.moveTo(12, 3.5f);
                p.cubicTo(12, 3.5f, 5.5f, 10.5f, 5.5f, 14.5f);
                p.cubicTo(5.5f, 18.2f, 8.4f, 20.5f, 12, 20.5f);
                p.cubicTo(15.6f, 20.5f, 18.5f, 18.2f, 18.5f, 14.5f);
                p.cubicTo(18.5f, 10.5f, 12, 3.5f, 12, 3.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_TEXT:
                p.moveTo(5.5f, 19.5f);
                p.lineTo(12, 4.5f);
                p.lineTo(18.5f, 19.5f);
                c.drawPath(p, mStroke);
                c.drawLine(8, 14, 16, 14, mStroke);
                break;
            case ICON_SIZE:
                c.drawLine(12, 4, 12, 20, mStroke);
                p.moveTo(8.5f, 7.5f);
                p.lineTo(12, 4);
                p.lineTo(15.5f, 7.5f);
                p.moveTo(8.5f, 16.5f);
                p.lineTo(12, 20);
                p.lineTo(15.5f, 16.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_DIR:
                p.moveTo(3.5f, 6);
                p.lineTo(9.5f, 6);
                p.lineTo(11.5f, 8.5f);
                p.lineTo(20.5f, 8.5f);
                p.lineTo(20.5f, 18.5f);
                p.lineTo(3.5f, 18.5f);
                p.close();
                c.drawPath(p, mStroke);
                break;
            case ICON_CLOCK:
                c.drawCircle(12, 12, 8.5f, mStroke);
                p.moveTo(12, 7.5f);
                p.lineTo(12, 12);
                p.lineTo(15.2f, 14);
                c.drawPath(p, mStroke);
                break;
            case ICON_LOCK:
                mRect.set(6, 10.5f, 18, 20);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                mRect.set(8.5f, 4, 15.5f, 13);
                c.drawArc(mRect, 180, 180, false, mStroke);
                c.drawLine(8.5f, 8.5f, 8.5f, 10.5f, mStroke);
                c.drawLine(15.5f, 8.5f, 15.5f, 10.5f, mStroke);
                break;
            case ICON_BRANCH:
                c.drawCircle(6, 6, 2.2f, mStroke);
                c.drawCircle(6, 18, 2.2f, mStroke);
                c.drawCircle(18, 9, 2.2f, mStroke);
                c.drawLine(6, 8.2f, 6, 15.8f, mStroke);
                p.moveTo(18, 11.2f);
                p.cubicTo(18, 15, 6, 12.5f, 6, 15.8f);
                c.drawPath(p, mStroke);
                break;
            case ICON_BELL:
                p.moveTo(6, 17);
                p.lineTo(6, 11);
                p.cubicTo(6, 7, 8.5f, 5, 12, 5);
                p.cubicTo(15.5f, 5, 18, 7, 18, 11);
                p.lineTo(18, 17);
                c.drawPath(p, mStroke);
                c.drawLine(4.5f, 17, 19.5f, 17, mStroke);
                c.drawLine(10.5f, 20, 13.5f, 20, mStroke);
                break;
            case ICON_WINDOW:
                mRect.set(3.5f, 5, 20.5f, 19);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                c.drawLine(3.5f, 9, 20.5f, 9, mStroke);
                break;
            case ICON_GRID:
                mRect.set(4, 4, 10.5f, 10.5f);
                c.drawRoundRect(mRect, 1, 1, mStroke);
                mRect.set(13.5f, 4, 20, 10.5f);
                c.drawRoundRect(mRect, 1, 1, mStroke);
                mRect.set(4, 13.5f, 10.5f, 20);
                c.drawRoundRect(mRect, 1, 1, mStroke);
                mRect.set(13.5f, 13.5f, 20, 20);
                c.drawRoundRect(mRect, 1, 1, mStroke);
                break;
            case ICON_BATTERY:
                mRect.set(3.5f, 8, 18.5f, 16);
                c.drawRoundRect(mRect, 1.5f, 1.5f, mStroke);
                c.drawLine(21, 10.5f, 21, 13.5f, mStroke);
                c.drawLine(6.5f, 12, 12, 12, mStroke);
                break;
            case ICON_LAYERS:
                p.moveTo(12, 4);
                p.lineTo(20.5f, 9);
                p.lineTo(12, 14);
                p.lineTo(3.5f, 9);
                p.close();
                p.moveTo(3.5f, 13.5f);
                p.lineTo(12, 18.5f);
                p.lineTo(20.5f, 13.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_SORT:
                c.drawLine(4.5f, 7, 13, 7, mStroke);
                c.drawLine(4.5f, 12, 10.5f, 12, mStroke);
                c.drawLine(4.5f, 17, 8, 17, mStroke);
                c.drawLine(17.5f, 6, 17.5f, 18, mStroke);
                p.moveTo(15, 15.5f);
                p.lineTo(17.5f, 18);
                p.lineTo(20, 15.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_TERMINAL:
                mRect.set(3.5f, 4.5f, 20.5f, 19.5f);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                p.moveTo(7, 9);
                p.lineTo(10.5f, 12);
                p.lineTo(7, 15);
                c.drawPath(p, mStroke);
                c.drawLine(12.5f, 15, 16.5f, 15, mStroke);
                break;
            case ICON_KEYBOARD:
                mRect.set(2.5f, 6.5f, 21.5f, 17.5f);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                c.drawLine(6, 10.3f, 7, 10.3f, mStroke);
                c.drawLine(9.7f, 10.3f, 10.7f, 10.3f, mStroke);
                c.drawLine(13.3f, 10.3f, 14.3f, 10.3f, mStroke);
                c.drawLine(17, 10.3f, 18, 10.3f, mStroke);
                c.drawLine(8, 14, 16, 14, mStroke);
                break;
            case ICON_BAR:
                mRect.set(3.5f, 4.5f, 20.5f, 19.5f);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                c.drawLine(3.5f, 9.5f, 20.5f, 9.5f, mStroke);
                c.drawLine(6.5f, 7, 9.5f, 7, mStroke);
                break;
            case ICON_DIALOG:
                mRect.set(3.5f, 5.5f, 20.5f, 16);
                c.drawRoundRect(mRect, 2, 2, mStroke);
                p.moveTo(8, 16);
                p.lineTo(8, 20);
                p.lineTo(12.5f, 16);
                c.drawPath(p, mStroke);
                break;
            case ICON_PLUG:
                c.drawLine(9, 3.5f, 9, 8, mStroke);
                c.drawLine(15, 3.5f, 15, 8, mStroke);
                p.moveTo(6.5f, 8);
                p.lineTo(17.5f, 8);
                p.lineTo(17.5f, 11.5f);
                p.cubicTo(17.5f, 14.5f, 15, 16.5f, 12, 16.5f);
                p.cubicTo(9, 16.5f, 6.5f, 14.5f, 6.5f, 11.5f);
                p.close();
                c.drawPath(p, mStroke);
                c.drawLine(12, 16.5f, 12, 20.5f, mStroke);
                break;
            case ICON_TOGGLE:
                mRect.set(3, 7.5f, 21, 16.5f);
                c.drawRoundRect(mRect, 4.5f, 4.5f, mStroke);
                c.drawCircle(16.5f, 12, 2.2f, mFill);
                break;
            case ICON_FRAME:
                mRect.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawRoundRect(mRect, 1.5f, 1.5f, mStroke);
                break;
            case ICON_CORNER:
                p.moveTo(5, 19);
                p.lineTo(5, 11);
                p.cubicTo(5, 7.5f, 7.5f, 5, 11, 5);
                p.lineTo(19, 5);
                c.drawPath(p, mStroke);
                break;
            case ICON_SQUARE:
                mRect.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawRoundRect(mRect, 1.5f, 1.5f, mStroke);
                mRect.set(8.5f, 8.5f, 15.5f, 15.5f);
                c.drawRect(mRect, mFill);
                break;
            case ICON_EXPORT:
                c.drawLine(8, 19, 8, 5, mStroke);
                c.drawLine(16, 5, 16, 19, mStroke);
                p.moveTo(4.5f, 8.5f);
                p.lineTo(8, 5);
                p.lineTo(11.5f, 8.5f);
                p.moveTo(12.5f, 15.5f);
                p.lineTo(16, 19);
                p.lineTo(19.5f, 15.5f);
                c.drawPath(p, mStroke);
                break;
            case ICON_ADD:
                c.drawLine(12, 5, 12, 19, mStroke);
                c.drawLine(5, 12, 19, 12, mStroke);
                break;
            case ICON_PAGE:
                mRect.set(5, 3.5f, 19, 20.5f);
                c.drawRoundRect(mRect, 1.5f, 1.5f, mStroke);
                c.drawLine(8, 8, 16, 8, mStroke);
                c.drawLine(8, 12, 16, 12, mStroke);
                c.drawLine(8, 16, 13, 16, mStroke);
                break;
            case ICON_DOT:
            default:
                c.drawCircle(12, 12, 3, mStroke);
                break;
        }
    }

    /** One slider: a line across at {@code y}, broken by a round knob at {@code knobX}. */
    private void slider(@NonNull Canvas c, float y, float knobX) {
        c.drawLine(4, y, knobX - 2.2f, y, mStroke);
        c.drawCircle(knobX, y, 2.2f, mStroke);
        c.drawLine(knobX + 2.2f, y, 20, y, mStroke);
    }

    /** Eight spokes around the centre, from radius {@code from} to {@code to}. */
    private void rays(@NonNull Canvas c, float from, float to) {
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(12 + from * cos, 12 + from * sin, 12 + to * cos, 12 + to * sin, mStroke);
        }
    }

    @Override
    public void setTintList(@Nullable ColorStateList tint) {
        if (tint != null) setColor(tint.getColorForState(getState(), tint.getDefaultColor()));
    }

    @Override
    public void setTint(int tintColor) {
        setColor(tintColor);
    }

    @Override
    public int getIntrinsicWidth() {
        return mSizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSizePx;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
