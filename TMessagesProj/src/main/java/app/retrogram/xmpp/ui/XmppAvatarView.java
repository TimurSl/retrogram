package app.retrogram.xmpp.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

import app.retrogram.xmpp.XmppAvatars;
import app.retrogram.xmpp.core.XmppShow;

/** A round avatar: the contact's vCard photo if there is one, otherwise coloured initials, with an optional presence dot. */
public class XmppAvatarView extends View {
    private final AvatarDrawable fallback = new AvatarDrawable();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private BitmapShader shader;
    private XmppShow presence;

    public XmppAvatarView(Context context) {
        super(context);
        fallback.setTextSize(dp(18));
    }

    /**
     * @param name   what the initials come from (display name or JID)
     * @param photo  true to look for a vCard photo (contacts); rooms and occupants use initials only
     * @param show   non-null to draw a presence dot
     */
    public void set(long accountId, String jid, String name, boolean photo, XmppShow show) {
        fallback.setInfo(Math.abs((long) jid.toLowerCase().hashCode()), name == null || name.isEmpty() ? jid : name, "");
        Bitmap b = photo ? XmppAvatars.get(accountId, jid) : null;
        if (b != bitmap) {
            bitmap = b;
            shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        presence = show;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float r = Math.min(w, h) / 2f;
        if (shader != null) {
            matrix.reset();
            float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
            matrix.postScale(scale, scale);
            matrix.postTranslate((w - bitmap.getWidth() * scale) / 2f, (h - bitmap.getHeight() * scale) / 2f);
            shader.setLocalMatrix(matrix);
            bitmapPaint.setShader(shader);
            canvas.drawCircle(w / 2f, h / 2f, r, bitmapPaint);
        } else {
            fallback.setBounds(0, 0, w, h);
            fallback.setRoundRadius((int) r);
            fallback.draw(canvas);
        }
        if (presence != null && presence != XmppShow.OFFLINE) {
            float dot = Math.max(dp(5), r * 0.26f);
            float cx = w - dot - dp(1);
            float cy = h - dot - dp(1);
            dotPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            canvas.drawCircle(cx, cy, dot + dp(1.5f), dotPaint);
            dotPaint.setColor(XmppUi.showColor(presence));
            canvas.drawCircle(cx, cy, dot, dotPaint);
        }
    }
}
