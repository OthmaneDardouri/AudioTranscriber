package com.othmane.vocaleai.bubble

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.othmane.vocaleai.R
import com.othmane.vocaleai.data.Prefs
import com.othmane.vocaleai.ui.MainActivity
import com.othmane.vocaleai.ui.TranscribeActivity
import kotlin.math.abs

data class PendingVoice(val uri: Uri?, val sender: String, val time: Long)

/**
 * Bolla rotonda con il microfono che resta sopra le altre app (anche sopra WhatsApp).
 * Tocco = trascrivi l'ultimo vocale · trascina = sposta · pressione lunga = chiudi.
 */
object BubbleManager {
    private const val AUTO_HIDE_MS = 5 * 60_000L
    private const val CHANNEL_ID = "voice_notes"
    private const val ACCENT = 0xFF00A884.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<PendingVoice>()
    private var root: View? = null
    private var badge: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val autoHide = Runnable { hide() }

    fun canDrawOverlay(context: Context) = Settings.canDrawOverlays(context)

    /** Punto d'ingresso dal listener: bolla se possibile, altrimenti notifica. */
    fun announce(context: Context, item: PendingVoice) {
        if (Prefs.bubbleEnabled(context) && canDrawOverlay(context)) {
            push(context.applicationContext, item)
        } else {
            postNotification(context, item)
        }
    }

    fun push(context: Context, item: PendingVoice) {
        queue.addLast(item)
        while (queue.size > 10) queue.removeFirst()
        if (root == null) create(context)
        refreshBadge()
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, AUTO_HIDE_MS)
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val v = root ?: return
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
        root = null
        badge = null
        params = null
        queue.clear()
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun create(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = dp(context, 58f)
        val metrics = context.resources.displayMetrics

        val circle = ImageView(context).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(Color.WHITE)
            val pad = dp(context, 15f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT)
                setStroke(dp(context, 2f), Color.WHITE)
            }
            elevation = dp(context, 6f).toFloat()
            contentDescription = "Trascrivi vocale"
        }
        val badgeView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE53935.toInt())
            }
            elevation = dp(context, 8f).toFloat()
            visibility = View.GONE
        }
        val container = FrameLayout(context).apply {
            val inner = size
            addView(circle, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
            val b = dp(context, 20f)
            addView(badgeView, FrameLayout.LayoutParams(b, b, Gravity.TOP or Gravity.END))
            val p = dp(context, 4f)
            setPadding(p, p, p, p)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size - dp(context, 16f)
            y = (metrics.heightPixels * 0.35f).toInt()
        }

        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val longPress = ViewConfiguration.getLongPressTimeout().toLong()
        container.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        downX = e.rawX; downY = e.rawY
                        moved = false
                        circle.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                        if (moved) {
                            lp.x = startX + dx
                            lp.y = startY + dy
                            try { wm.updateViewLayout(container, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        circle.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        if (e.actionMasked == MotionEvent.ACTION_CANCEL) return true
                        if (moved) {
                            snapToEdge(context, wm, container, lp)
                        } else if (e.eventTime - e.downTime >= longPress) {
                            hide()
                        } else {
                            onTap(context)
                        }
                    }
                }
                return true
            }
        })

        try {
            wm.addView(container, lp)
            root = container
            badge = badgeView
            params = lp
        } catch (e: Exception) {
            // Permesso revocato nel frattempo: ripiega sulla notifica.
            queue.lastOrNull()?.let { postNotification(context, it) }
            queue.clear()
        }
    }

    private fun snapToEdge(context: Context, wm: WindowManager, v: View, lp: WindowManager.LayoutParams) {
        val w = context.resources.displayMetrics.widthPixels
        val margin = dp(context, 8f)
        lp.x = if (lp.x + v.width / 2 < w / 2) margin else w - v.width - margin
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    private fun refreshBadge() {
        val b = badge ?: return
        if (queue.size > 1) {
            b.text = queue.size.toString()
            b.visibility = View.VISIBLE
        } else {
            b.visibility = View.GONE
        }
    }

    private fun onTap(context: Context) {
        val item = queue.removeLastOrNull()
        context.startActivity(intentFor(context, item))
        if (queue.isEmpty()) hide() else refreshBadge()
    }

    private fun intentFor(context: Context, item: PendingVoice?): Intent {
        return if (item != null && item.uri != null) {
            TranscribeActivity.intent(context, item.uri, item.sender).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        } else {
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nuovi vocali", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avviso per trascrivere i vocali WhatsApp quando la bolla non è disponibile"
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, item: PendingVoice) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(context)
        val requestCode = (item.time % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getActivity(
            context, requestCode, intentFor(context, item),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setColor(ACCENT)
            .setContentTitle("🎤 Vocale da ${item.sender}")
            .setContentText("Tocca per trascriverlo e riassumerlo")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nmc.notify(requestCode, notif)
        } catch (_: SecurityException) {
        }
    }
}
